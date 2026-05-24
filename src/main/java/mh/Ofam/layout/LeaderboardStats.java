package mh.Ofam.layout;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

@Slf4j
public class LeaderboardStats {

    public static enum SCORE_METRIC {
        GEOMETRIC_AVERAGE,
        PAIN_SCORE,
        ELITE_SCORE
    }

    public static LeaderboardStats instance(Weights weights, List<LayoutStats> allResults, SCORE_METRIC metric) {

        LeaderboardStats leaderboardStats = new LeaderboardStats();

        // Výpočet váženého skóre a zoradenie (Zostupne - najlepší všestranný stred je prvý)
        leaderboardStats.calculateWeightedBalancedScores(weights, allResults);
        leaderboardStats.calculatePainScore(weights, allResults);
        switch (metric) {
            case GEOMETRIC_AVERAGE -> {
                // Už máme vypočítané balancedScore, takže len zoradíme podľa toho
                allResults.sort(Comparator.comparingDouble(LayoutStats::getBalancedScore).reversed());
            }
            case PAIN_SCORE -> {
                // Už máme vypočítané fingerPainScore, takže len zoradíme podle toho
                allResults.sort(Comparator.comparingDouble(LayoutStats::getFingerPainScore));
            }
            case ELITE_SCORE -> {
                allResults.sort(Comparator.comparingDouble(LayoutStats::getEliteScore).reversed());
            }
        }

        // Tlač záverečného sumárneho rebríčka pre rýchle globálne porovnanie
        leaderboardStats.printLeaderboard(allResults, "Zero Pain & Finger Protection");

        return leaderboardStats;
    }

    // --- METÓDA PRE MATEMATICKÚ DYNAMICKÚ VÁHU S PEVNÝMI MANTINELMI ---
    public void calculateWeightedBalancedScores(Weights weights, List<LayoutStats> results) {
        if (results.isEmpty()) return;

        double totalWeight = weights.totalWeight();
        if (totalWeight == 0) return;

        for (LayoutStats r : results) {
            // PARAMETRE: (aktuálna hodnota, ideálny cieľ, najhoršia akceptovateľná hodnota)

            // --- TRESTY (Menšie je lepšie) ---
            double sfbScore = getAbsoluteScore(r.getSfbPct(), 1.0, 5.0);        // <1% je dokonalé, >5% je odpad
            double sfsScore = getAbsoluteScore(r.getSfsPct(), 4.0, 9.0);
            double stretchScore = getAbsoluteScore(r.getInnerStretchPct(), 5.0, 16.0); // Na Toteme chceme stretch pod 5%
            double scissorsScore = getAbsoluteScore(r.getScissorsPct(), 0.5, 3.0);
            double redirectsScore = getAbsoluteScore(r.getRedirectsPct(), 3.0, 12.0); // Pod 3% je luxus, nad 12% je pinball (Colemak)

            // TRESTY ZA PREŤAŽENIE A FLÁKANIE SA (Lichobežníková tolerancia)
            // Parametre: (hodnota, lowWorst, lowBest, highBest, highWorst)

            // Malíčky: Dovolíme im flákať sa, ale prísne trestáme preťaženie (>9%)
            double lPinkyScore = getAbsoluteScore(r.lPinky, 0.0, 1.0, 9.0, 12.0);
            double rPinkyScore = getAbsoluteScore(r.rPinky, 0.0, 1.0, 9.0, 12.0);

            // Prstenníky: Široká zóna tolerancie (5% - 20%)
            double lRingScore = getAbsoluteScore(r.lRing, 2.0, 5.0, 20.0, 24.0);
            double rRingScore = getAbsoluteScore(r.rRing, 2.0, 5.0, 20.0, 24.0);

            // Prostredníky: Stredná pracovná sila (8% - 22%)
            double lMiddleScore = getAbsoluteScore(r.lMiddle, 5.0, 8.0, 22.0, 26.0);
            double rMiddleScore = getAbsoluteScore(r.rMiddle, 5.0, 8.0, 22.0, 26.0);

            // Ukazováky: Najsilnejšie prsty. MUSIA urobiť aspoň 12%, inak layout plytvá ich potenciálom!
            double lIndexScore = getAbsoluteScore(r.lIndex, 9.0, 12.0, 26.0, 30.0);
            double rIndexScore = getAbsoluteScore(r.rIndex, 9.0, 12.0, 26.0, 30.0);

            // Zlúčenie násobením: Väčšina prstov bude 1.0.
            // Ak nejaký prst vybočí, skóre klesne. Ak vybočia viaceré, tresty sa tvrdo zosobášia.
            double maxFingerScore = lPinkyScore * rPinkyScore * lRingScore * rRingScore * lMiddleScore * rMiddleScore * lIndexScore * rIndexScore;
            r.fingerScore = maxFingerScore;

            double lsbScore = getAbsoluteScore(r.getLsbPct(), 1.0, 6.0);
            double lhbScore = getAbsoluteScore(r.getLhbPct(), 1.0, 5.0);

            // Ideál je pod 0,15 % úderov, odpad je všetko nad 1,2 %
            double runsScore = getAbsoluteScore(r.getRunsPct(), 0.15, 1.20);

            // --- ODMENY (Väčšie je lepšie -> otočené hodnoty) ---
            double altScore = getAbsoluteScore(r.getAlternationPct(), 75.0, 40.0);      // 75% alt je strop (Dvorak)
            double rollInScore = getAbsoluteScore(r.getRollInPct(), 15.0, 0.0);
            double rollOutScore = getAbsoluteScore(r.getRollOutPct(), 10.0, 0.0);
            double homeRowScore = getAbsoluteScore(r.getHomeRowPct(), 70.0, 45.0);      // 70% na home row je sen

            // Vážený geometrický priemer
            double poweredProduct =
                    Math.pow(sfbScore, weights.getSfb()) *
                            Math.pow(sfsScore, weights.getSfs()) *
                            Math.pow(stretchScore, weights.getStretch()) *
                            Math.pow(altScore, weights.getAlt()) *
                            Math.pow(runsScore, weights.getRuns()) *
                            Math.pow(scissorsScore, weights.getScissors()) *
                            Math.pow(redirectsScore, weights.getRedirects()) *
                            Math.pow(rollInScore, weights.getRollIn()) *
                            Math.pow(rollOutScore, weights.getRollOut()) *
                            Math.pow(maxFingerScore, weights.getFingerStats()) *
                            Math.pow(lsbScore, weights.getLsb()) *
                            Math.pow(lhbScore, weights.getLhb()) *
                            Math.pow(homeRowScore, weights.getHomeRow());

            r.balancedScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0;
        }
    }

    public void calculatePainScore(Weights weights, List<LayoutStats> results) {
        if (results.isEmpty()) return;

        double totalWeight = weights.totalWeight();
        if (totalWeight == 0) return;

        for (LayoutStats r : results) {
            // --- VÝPOČET INDEXU BOLESTI (WEIGHTED PENALTY SUM) ---
// Čím nižšie číslo, tým lepší layout! (0 = bezbolestná dokonalosť)

            double painScore = 0.0;

// 1. KRVAVÉ CHYBY (Priama penalizácia z percent)
            painScore += r.getSfbPct() * weights.getSfb();
            painScore += r.getRedirectsPct() * weights.getRedirects();
            painScore += r.getLhbPct() * weights.getLhb();

// 2. SEKUNDÁRNE CHYBY
            painScore += r.getSfsPct() * weights.getSfs();
            painScore += r.getLsbPct() * weights.getLsb();
            painScore += r.getInnerStretchPct() * weights.getStretch();

// 3. ANATOMICKÉ TRESTY PRE KAŽDÝ PRST OSOBITNE (OVERLOAD)
// (Biológia je pevná, váha trestu je dynamická)

// Malíčky (limit 9%)
            painScore += Math.max(0, r.lPinky - 9.0) * weights.getPinkyPenalty();
            painScore += Math.max(0, r.rPinky - 9.0) * weights.getPinkyPenalty();

// Prstenníky (limit 18%)
            painScore += Math.max(0, r.lRing - 18.0) * weights.getRingPenalty();
            painScore += Math.max(0, r.rRing - 18.0) * weights.getRingPenalty();

// Prostredníky (limit 24%)
            painScore += Math.max(0, r.lMiddle - 24.0) * weights.getMiddlePenalty();
            painScore += Math.max(0, r.rMiddle - 24.0) * weights.getMiddlePenalty();

// Ukazováky (limit 28% pre preťaženie)
            painScore += Math.max(0, r.lIndex - 28.0) * weights.getIndexPenalty();
            painScore += Math.max(0, r.rIndex - 28.0) * weights.getIndexPenalty();

// 4. FLÁKANIE SA UKAZOVÁKOV (UNDERLOAD)
// Každý ukazovák OSOBITNE musí urobiť aspoň 12%. Ak nie, dostane trest.
            painScore += Math.max(0, 12.0 - r.lIndex) * weights.getIndexUnderloadPenalty();
            painScore += Math.max(0, 12.0 - r.rIndex) * weights.getIndexUnderloadPenalty();
// Flákanie sa prostredníkov (Musia urobiť aspoň 8%)
            painScore += Math.max(0, 8.0 - r.lMiddle) * weights.getMiddleUnderloadPenalty();
            painScore += Math.max(0, 8.0 - r.rMiddle) * weights.getMiddleUnderloadPenalty();

// 5. ODMENY (Znižujú výsledné skóre)
            painScore -= r.getAlternationPct() * weights.getAlt();
            painScore -= r.getHomeRowPct() * weights.getHomeRow();

            r.fingerPainScore = painScore;
        }
    }

    // --- UNIVERZÁLNA POMOCNÁ METÓDA NA PEVNÉ ŠKÁLOVANIE ---
    private double getAbsoluteScore(double value, double best, double worst) {
        if (best < worst) {
            // Logika pre tresty (čím menej, tým lepšie)
            if (value <= best) return 1.0;
            if (value >= worst) return 0.01;
            return 0.01 + 0.99 * ((worst - value) / (worst - best));
        } else {
            // Logika pre odmeny (čím viac, tým lepšie)
            if (value >= best) return 1.0;
            if (value <= worst) return 0.01;
            return 0.01 + 0.99 * ((value - worst) / (best - worst));
        }
    }

    private double getAbsoluteScore(double value, double lowWorst, double lowBest, double highBest, double highWorst) {
        final double LOW_COEF = 0.1;
        final double HIGH_COEF = 1 - LOW_COEF;
        // priklad lw 1, lb 2, hb 3, hw 4
        if (value <= lowWorst) return LOW_COEF; // <= 1
        if (value >= lowWorst && value <= lowBest)
            return LOW_COEF + HIGH_COEF * ((value - lowWorst) / (lowBest - lowWorst)); // 1 az 2
        if (value >= lowBest && value <= highBest) return LOW_COEF + HIGH_COEF; // 2 az 3
        if (value >= highBest && value <= highWorst)
            return LOW_COEF + HIGH_COEF * ((highWorst - value) / (highWorst - highBest)); // 3 az 4
        if (value >= highWorst) return LOW_COEF; // >= 4
        // should not happen
        return 1000000000;
    }

    // --- PREHĽADNÝ SUMÁRNY LEADERBOARD PRE VŠETKY METRIKY ---
    public void printLeaderboard(List<LayoutStats> results, String sortedByCriteria) {
        System.out.println("\n=====================================================================================================================================================================================================================================");
        System.out.println("   VÝSLEDNÁ LISTINA LAYOUTOV (Zoradené podľa: " + sortedByCriteria.toUpperCase() + ")");
        System.out.println("=====================================================================================================================================================================================================================================");

        // Hlavička tabuľky (Pridané: Scis%, RollI%, RollO%)
        System.out.printf("%-4s | %-20s | %-6s | %-6s | %-6s | %-4s | %-4s | %-4s | %-4s | %-4s | %-4s | %-4s | %-4s | %-6s | %-6s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-6s | %-6s | %-5s\n",
                "Idx", "Názov layoutu", "ELITE", "BAL_S", "PAIN_S", "LP%", "LR%", "LM%", "LI%", "RI%", "RM%", "RR%", "RP%", "InStr%", "Redir%", "SFB%", "SFS%", "Scis%", "LSB%", "LHB%", "Home%", "Alt%", "RollI%", "RollO%", "Runs%");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

        int i = 0;
        for (LayoutStats res : results) {
            System.out.printf("%-4d | %-20s | %6.4f | %6.2f | %6.2f | %4.1f | %4.1f | %4.1f | %4.1f | %4.1f | %4.1f | %4.1f | %4.1f | %6.2f | %6.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %6.2f | %6.2f | %5.2f\n",
                    ++i,
                    res.getLayout().name,
                    res.getEliteScore(),
                    res.getBalancedScore(),
                    res.getFingerPainScore(),
                    res.lPinky,
                    res.lRing,
                    res.lMiddle,
                    res.lIndex,
                    res.rIndex,
                    res.rMiddle,
                    res.rRing,
                    res.rPinky,
                    res.getInnerStretchPct(),
                    res.getRedirectsPct(),
                    res.getSfbPct(),
                    res.getSfsPct(),
                    res.getScissorsPct(),         // DOPLNENÉ
                    res.getLsbPct(),
                    res.getLhbPct(),
                    res.getHomeRowPct(),
                    res.getAlternationPct(),
                    res.getRollInPct(),           // DOPLNENÉ
                    res.getRollOutPct(),          // DOPLNENÉ
                    res.getRunsPct()
            );
        }
        System.out.println("=====================================================================================================================================================================================================================================");
    }

}
