package mh.Ofam.layout;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LeaderboardStats {

    public static LeaderboardStats instance(Weights weights, List<LayoutStats> allResults) {
        LeaderboardStats leaderboardStats = new LeaderboardStats();

        // Výpočet váženého skóre a zoradenie (Zostupne - najlepší všestranný stred je prvý)
        leaderboardStats.calculateWeightedBalancedScores(weights, allResults);
        allResults.sort(Comparator.comparingDouble(LayoutStats::getBalancedScore).reversed());

        // Tlač záverečného sumárneho rebríčka pre rýchle globálne porovnanie
        leaderboardStats.printLeaderboard(allResults, "Zero Pain & Finger Protection");

        return leaderboardStats;
    }

    // --- METÓDA PRE MATEMATICKÚ DYNAMICKÚ VÁHU S PEVNÝMI MANTINELMI ---
    public void calculateWeightedBalancedScores(Weights weights, List<LayoutStats> results) {
        if (results.isEmpty()) return;

        double totalWeight = weights.getSfb() + weights.getSfs() + weights.getStretch() +
                weights.getAlt() + weights.getRuns() + weights.getScissors() +
                weights.getRedirects() + weights.getRollIn() + weights.getRollOut() +
                weights.getMaxFinger() + weights.getLsb() + weights.getLhb() + weights.getHomeRow();

        if (totalWeight == 0) return;

        for (LayoutStats r : results) {
            // PARAMETRE: (aktuálna hodnota, ideálny cieľ, najhoršia akceptovateľná hodnota)

            // --- TRESTY (Menšie je lepšie) ---
            double sfbScore = getAbsoluteScore(r.getSfbPct(), 1.0, 5.0);        // <1% je dokonalé, >5% je odpad
            double sfsScore = getAbsoluteScore(r.getSfsPct(), 4.0, 9.0);
            double stretchScore = getAbsoluteScore(r.getInnerStretchPct(), 5.0, 16.0); // Na Toteme chceme stretch pod 5%
            double scissorsScore = getAbsoluteScore(r.getScissorsPct(), 0.5, 3.0);
            double redirectsScore = getAbsoluteScore(r.getRedirectsPct(), 3.0, 12.0); // Pod 3% je luxus, nad 12% je pinball (Colemak)

            double maxFingerScore = getAbsoluteScore(r.getMaxFingerLoadPct(), 16.0, 22.0); // Fér záťaž prstov
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
                            Math.pow(maxFingerScore, weights.getMaxFinger()) *
                            Math.pow(lsbScore, weights.getLsb()) *
                            Math.pow(lhbScore, weights.getLhb()) *
                            Math.pow(homeRowScore, weights.getHomeRow());

            r.balancedScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0;
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
    // --- PREHĽADNÝ SUMÁRNY LEADERBOARD PRE NOVÉ METRIKY ---
    public void printLeaderboard(List<LayoutStats> results, String sortedByCriteria) {
        System.out.println("\n=========================================================================================================================================");
        System.out.println("   VÝSLEDNÁ LISTINA LAYOUTOV (Zoradené podľa: " + sortedByCriteria.toUpperCase() + ")");
        System.out.println("=========================================================================================================================================");
        System.out.printf("%-5s | %-22s | %-9s | %-9s | %-10s | %-8s | %-7s | %-7s | %-7s | %-7s | %-8s | %-8s | %-10s\n",
                "Idx", "Názov layoutu", "BAL_SCORE", "MaxFing %", "InnerStr %", "Redir %", "SFB %", "SFS %", "LSB %", "LHB %", "HomeRow%", "Alt %", "Jednoruké runs %");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------");

        int i = 0;
        for (LayoutStats res : results) {
            System.out.printf("%-5d | %-22s | %8.2f  | %7.2f %% | %8.2f %% | %6.2f %% | %5.2f %% | %5.2f %% | %5.2f %% | %5.2f %% | %6.2f %% | %6.2f %% | %6.2f\n",
                    ++i,
                    res.getLayout().name,
                    res.getBalancedScore(),
                    res.getMaxFingerLoadPct(),
                    res.getInnerStretchPct(),
                    res.getRedirectsPct(),
                    res.getSfbPct(),
                    res.getSfsPct(),
                    res.getLsbPct(),
                    res.getLhbPct(),
                    res.getHomeRowPct(),
                    res.getAlternationPct(),
                    res.getRunsPct()
            );
        }
        System.out.println("=========================================================================================================================================");
    }}
