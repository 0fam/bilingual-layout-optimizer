package mh.Ofam.layout;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class LeaderboardStats {

    public static LeaderboardStats instance(Weights weights, List<AnalysisResult> allResults) {
        LeaderboardStats leaderboardStats = new LeaderboardStats();

        // Výpočet váženého skóre a zoradenie (Zostupne - najlepší všestranný stred je prvý)
        leaderboardStats.calculateWeightedBalancedScores(weights, allResults);
        allResults.sort(Comparator.comparingDouble(AnalysisResult::getBalancedScore).reversed());

        // Tlač záverečného sumárneho rebríčka pre rýchle globálne porovnanie
        leaderboardStats.printLeaderboard(allResults, "Užívateľské vážené skóre");

        return leaderboardStats;
    }

    // --- NOVÁ METÓDA PRE MATEMATICKÚ DYNAMICKÚ VÁHUTU (GEOMETRICKÝ PRIEMER) ---
    public void calculateWeightedBalancedScores(Weights weights, List<AnalysisResult> results) {
        if (results.isEmpty()) return;

        double minSfb = Double.MAX_VALUE, maxSfb = Double.MIN_VALUE;
        double minSfs = Double.MAX_VALUE, maxSfs = Double.MIN_VALUE;
        double minStretch = Double.MAX_VALUE, maxStretch = Double.MIN_VALUE;
        double minAlt = Double.MAX_VALUE, maxAlt = Double.MIN_VALUE;
        double minRuns = Double.MAX_VALUE, maxRuns = Double.MIN_VALUE;
        // Nové metriky
        double minScissors = Double.MAX_VALUE, maxScissors = Double.MIN_VALUE;
        double minRedirects = Double.MAX_VALUE, maxRedirects = Double.MIN_VALUE;
        double minRollIn = Double.MAX_VALUE, maxRollIn = Double.MIN_VALUE;
        double minRollOut = Double.MAX_VALUE, maxRollOut = Double.MIN_VALUE;

        // Vytiahneme váhy z mapy
        double totalWeight = weights.getSfb() +
                weights.getSfs() +
                weights.getStretch() +
                weights.getAlt() +
                weights.getRuns() +
                weights.getScissors() +
                weights.getRedirects() +
                weights.getRollIn() +
                weights.getRollOut();

        // 1. Krok: Analýza rozsahu dát celého poolu
        for (AnalysisResult r : results) {
            minSfb = Math.min(minSfb, r.getSfbPct());
            maxSfb = Math.max(maxSfb, r.getSfbPct());
            minSfs = Math.min(minSfs, r.getSfsPct());
            maxSfs = Math.max(maxSfs, r.getSfsPct());
            minStretch = Math.min(minStretch, r.getInnerStretchPct());
            maxStretch = Math.max(maxStretch, r.getInnerStretchPct());
            minAlt = Math.min(minAlt, r.getAlternationPct());
            maxAlt = Math.max(maxAlt, r.getAlternationPct());
            minRuns = Math.min(minRuns, r.getLongOneHandedRuns());
            maxRuns = Math.max(maxRuns, r.getLongOneHandedRuns());
            minScissors = Math.min(minScissors, r.getScissorsPct());
            maxScissors = Math.max(maxScissors, r.getScissorsPct());
            minRedirects = Math.min(minRedirects, r.getRedirectsPct());
            maxRedirects = Math.max(maxRedirects, r.getRedirectsPct());
            minRollIn = Math.min(minRollIn, r.getRollInPct());
            maxRollIn = Math.max(maxRollIn, r.getRollInPct());
            minRollOut = Math.min(minRollOut, r.getRollOutPct());
            maxRollOut = Math.max(maxRollOut, r.getRollOutPct());

        }

        // 2. Krok: Škálovanie a aplikácia váženého geometrického priemeru
        for (AnalysisResult r : results) {
            // Tresty (menšie je lepšie)
            double sfbScore = maxSfb == minSfb ? 1.0 : 0.01 + 0.99 * ((maxSfb - r.getSfbPct()) / (maxSfb - minSfb));
            double sfsScore = maxSfs == minSfs ? 1.0 : 0.01 + 0.99 * ((maxSfs - r.getSfsPct()) / (maxSfs - minSfs));
            double stretchScore = maxStretch == minStretch ? 1.0 : 0.01 + 0.99 * ((maxStretch - r.getInnerStretchPct()) / (maxStretch - minStretch));
            double runsScore = maxRuns == minRuns ? 1.0 : 0.01 + 0.99 * ((maxRuns - r.getLongOneHandedRuns()) / (maxRuns - minRuns));
            double scissorsScore = maxScissors == minScissors ? 1.0 : 0.01 + 0.99 * ((maxScissors - r.getScissorsPct()) / (maxScissors - minScissors));
            double redirectsScore = maxRedirects == minRedirects ? 1.0 : 0.01 + 0.99 * ((maxRedirects - r.getRedirectsPct()) / (maxRedirects - minRedirects));

            // Odmeny (väčšie je lepšie -> otočená logika ako pri ALTs)
            double altScore = maxAlt == minAlt ? 1.0 : 0.01 + 0.99 * ((r.getAlternationPct() - minAlt) / (maxAlt - minAlt));
            double rollInScore = maxRollIn == minRollIn ? 1.0 : 0.01 + 0.99 * ((r.getRollInPct() - minRollIn) / (maxRollIn - minRollIn));
            double rollOutScore = maxRollOut == minRollOut ? 1.0 : 0.01 + 0.99 * ((r.getRollOutPct() - minRollOut) / (maxRollOut - minRollOut));

            // Vážený geometrický priemer: váha funguje ako exponent (mocnina)
            double poweredProduct =
                    Math.pow(sfbScore, weights.getSfb()) *
                            Math.pow(sfsScore, weights.getSfs()) *
                            Math.pow(stretchScore, weights.getStretch()) *
                            Math.pow(altScore, weights.getAlt()) *
                            Math.pow(runsScore, weights.getRuns()) *
                            Math.pow(scissorsScore, weights.getScissors()) *
                            Math.pow(redirectsScore, weights.getRedirects()) *
                            Math.pow(rollInScore, weights.getRollIn()) *
                            Math.pow(rollOutScore, weights.getRollOut());

            r.balancedScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0;
        }
    }

    // --- NOVÁ METÓDA PRE PREHĽADNÝ SUMÁRNY LEADERBOARD ---
    public void printLeaderboard(List<AnalysisResult> results, String sortedByCriteria) {
        System.out.println("\n====================================================================================================");
        System.out.println("   VÝSLEDNÁ LISTINA LAYOUTOV (Zoradené podľa: " + sortedByCriteria.toUpperCase() + ")");
        System.out.println("====================================================================================================");
        System.out.printf("%-5s | %-22s | %-9s | %-7s | %-7s | %-10s | %-12s | %-10s\n",
                "Idx", "Názov layoutu", "BAL_SCORE", "SFB %", "SFS %", "InnerStr %", "Alternácia %", "Jedn. behy");
        System.out.println("----------------------------------------------------------------------------------------------------");

        int i = 0;
        for (AnalysisResult res : results) {
            System.out.printf("%-5d | %-22s | %8.2f  | %5.2f %% | %5.2f %% | %8.2f %% | %10.2f %% | %dx\n",
                    ++i,
                    res.getLayoutName(),
                    res.getBalancedScore(),
                    res.getSfbPct(),
                    res.getSfsPct(),
                    res.getInnerStretchPct(),
                    res.getAlternationPct(),
                    res.getLongOneHandedRuns()
            );
        }
        System.out.println("====================================================================================================");
    }

}
