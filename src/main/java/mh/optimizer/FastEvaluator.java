package mh.optimizer;

import java.util.Map;

public class FastEvaluator {

    public static FastLayoutStats evaluate(FastLayout layout, NGramStats stats, Weights weights) {
        FastLayoutStats ls = new FastLayoutStats();

        long totalMonograms = 0;
        long[] rows = new long[3];
        long[] fingers = new long[8];
        double innerStretchScore = 0;

        // --- 1. ÚROVEŇ: MONOGRAMY (30 operácií) ---
        for (int i = 0; i < 30; i++) {
            char ch = layout.matrix[i];
            long freq = stats.monograms[ch];
            if (freq == 0) continue;

            totalMonograms += freq;
            rows[KeyboardTopology.POS_TO_ROW[i]] += freq;
            fingers[KeyboardTopology.POS_TO_FINGER[i]] += freq;

            // 🔥 Prepočítavame frekvenciu cez našu novú asymetrickú váhu nepohodlia
            innerStretchScore += freq * KeyboardTopology.POS_STRETCH_WEIGHT[i];
        }

        // --- 2. ÚROVEŇ: BIGRAMY A SKIPGRAMY (900 operácií) ---
        long totalBigrams = 0;
        long totalSkipgrams = 0;
        long handAlternation = 0;
        long sfb = 0;
        long sfs = 0;
        long scissors = 0;
        long lsb = 0;
        long lhb = 0;

        for (int i = 0; i < 30; i++) {
            char c1 = layout.matrix[i];
            int h1 = KeyboardTopology.POS_TO_HAND[i];
            int f1 = KeyboardTopology.POS_TO_FINGER[i];
            int r1 = KeyboardTopology.POS_TO_ROW[i];
            boolean s1 = KeyboardTopology.POS_STRETCH_WEIGHT[i] > 0.0;
            int col1 = i % 10;

            for (int j = 0; j < 30; j++) {
                char c2 = layout.matrix[j];

                // --- BIGRAMY ---
                // 🔥 ZMENA: Lookup cez bitový posun v plochom poli
                long bFreq = stats.bigrams[(c1 << 8) | c2];
                if (bFreq > 0) {
                    totalBigrams += bFreq;
                    int h2 = KeyboardTopology.POS_TO_HAND[j];
                    int f2 = KeyboardTopology.POS_TO_FINGER[j];
                    int r2 = KeyboardTopology.POS_TO_ROW[j];
                    boolean s2 = KeyboardTopology.POS_STRETCH_WEIGHT[j] > 0.0;
                    int col2 = j % 10;

                    if (h1 != h2) {
                        handAlternation += bFreq;
                    } else {
                        // Rovnaká ruka
                        if (f1 == f2 && i != j) {
                            sfb += bFreq;
                        }
                        // Nožnice
                        if (Math.abs(f1 - f2) == 1 && ((r1 == 0 && r2 == 2) || (r1 == 2 && r2 == 0))) {
                            scissors += bFreq;
                        }
                        // Lateral Stretch Bigram
                        if (s1 != s2 && f1 != f2) {
                            lsb += bFreq;
                        }
                        // Lateral Hand Bigram (Malíček-Prstenník kombinácie stĺpcov)
                        boolean p1 = (col1 == 0 || col1 == 1 || col1 == 8 || col1 == 9);
                        boolean p2 = (col2 == 0 || col2 == 1 || col2 == 8 || col2 == 9);
                        if (p1 && p2 && f1 != f2) {
                            lhb += bFreq;
                        }
                    }
                }

                // --- SKIPGRAMY ---
                // 🔥 ZMENA: Lookup cez bitový posun v plochom poli
                long sFreq = stats.skipgrams[(c1 << 8) | c2];
                if (sFreq > 0 && i != j) {
                    totalSkipgrams += sFreq;
                    int f2 = KeyboardTopology.POS_TO_FINGER[j];
                    int h2 = KeyboardTopology.POS_TO_HAND[j];
                    if (h1 == h2 && f1 == f2) {
                        sfs += sFreq;
                    }
                }
            }
        }

        // --- 3. ÚROVEŇ: TRIGRAMY (27 000 operácií) ---
        long totalTrigrams = 0;
        long inwardRolls = 0;
        long outwardRolls = 0;
        long redirects = 0;

        for (int i = 0; i < 30; i++) {
            char c1 = layout.matrix[i];
            int h1 = KeyboardTopology.POS_TO_HAND[i];
            int f1 = KeyboardTopology.POS_TO_FINGER[i];

            // 🔥 OPTIMALIZÁCIA: Predposun pre prvý znak
            int c1Shift = c1 << 16;

            for (int j = 0; j < 30; j++) {
                char c2 = layout.matrix[j];
                int h2 = KeyboardTopology.POS_TO_HAND[j];
                int f2 = KeyboardTopology.POS_TO_FINGER[j];

                // 🔥 OPTIMALIZÁCIA: Spojenie predposunu prvého a druhého znaku
                int c1c2Shift = c1Shift | (c2 << 8);

                for (int k = 0; k < 30; k++) {
                    char c3 = layout.matrix[k];

                    // 🔥 ZMENA: Priamy skok na finálnu adresu bez pointer chasing
                    long tFreq = stats.trigrams[c1c2Shift | c3];
                    if (tFreq == 0) continue;

                    totalTrigrams += tFreq;
                    int h3 = KeyboardTopology.POS_TO_HAND[k];
                    int f3 = KeyboardTopology.POS_TO_FINGER[k];

                    if (h1 == h2 && h2 == h3) {
                        // Valivé a zmeny smeru posudzujeme podľa ID prstov (0-7 zľava doprava)
                        if (f1 < f2 && f2 < f3) {
                            if (h1 == 0) inwardRolls += tFreq; else outwardRolls += tFreq; // Ľavá ruka dnu, pravá von
                        } else if (f1 > f2 && f2 > f3) {
                            if (h1 == 0) outwardRolls += tFreq; else inwardRolls += tFreq; // Ľavá ruka von, pravá dnu
                        } else if ((f2 > f1 && f2 > f3) || (f2 < f1 && f2 < f3)) {
                            redirects += tFreq;
                        }
                    }
                }
            }
        }

        // TOTO PRIDÁME DO FastEvaluatora PRE UNIVERZÁLNU ANALÝZU DLHÝCH BEHOV:
        long longOneHandedRuns = 0;

        // 🔥 ZMENA: Slučka prerobená na bleskový lineárny prelet cez primitívne polia
        int count = stats.longNGramsCount;
        long[] lFreqs = stats.longNGramsFreqs;
        int[] lLens = stats.longNGramsLengths;
        int[] lOffsets = stats.longNGramsOffsets;
        byte[] lConcat = stats.longNGramsConcat;

        for (int i = 0; i < count; i++) {
            int nLen = lLens[i];
            if (nLen == 0) continue;
            int offset = lOffsets[i];

            // Vytiahneme ruku prvého znaku priamo z lookup tabuľky layoutu
            int targetHand = layout.charToHand[lConcat[offset] & 0xFF];
            if (targetHand == -1) continue; // Neplatný znak

            boolean isSingleHanded = true;

            // Dynamicky skontrolujeme, či sú VŠETKY ostatné znaky na tej istej ruke
            for (int j = 1; j < nLen; j++) {
                int currentHand = layout.charToHand[lConcat[offset + j] & 0xFF];
                if (currentHand != targetHand) {
                    isSingleHanded = false;
                    break; // Ruka sa zmenila, prerušujeme kontrolu
                }
            }

            // Ak celá sekvencia prebehla na jednej ruke, vyhodnotíme metriku
            if (isSingleHanded && nLen == 5) {
                longOneHandedRuns += lFreqs[i]; // Našli sme tvoj 5-písmenový beh!
            }
        }

        // --- 4. PREPOČET PERCENTILOV ---
        double denomM = totalMonograms == 0 ? 1.0 : totalMonograms;
        double denomB = totalBigrams == 0 ? 1.0 : totalBigrams;
        double denomS = totalSkipgrams == 0 ? 1.0 : totalSkipgrams;
        double denomT = totalTrigrams == 0 ? 1.0 : totalTrigrams;

        ls.sfbPct = (sfb * 100.0) / denomB;
        ls.sfsPct = (sfs * 100.0) / denomS;
        ls.stretchPct = (innerStretchScore * 100.0) / denomM;
        ls.scissorsPct = (scissors * 100.0) / denomB;
        ls.redirectsPct = (redirects * 100.0) / denomT;
        ls.lsbPct = (lsb * 100.0) / denomB;
        ls.lhbPct = (lhb * 100.0) / denomB;
        ls.altPct = (handAlternation * 100.0) / denomB;
        ls.homeRowPct = (rows[1] * 100.0) / denomM;
        ls.rollInPct = (inwardRolls * 100.0) / denomT;
        ls.rollOutPct = (outwardRolls * 100.0) / denomT;
        ls.runsPct = (longOneHandedRuns * 100.0) / denomM;

        for (int i = 0; i < 8; i++) {
            ls.fingerLoads[i] = (fingers[i] * 100.0) / denomM;
        }

        // --- 5. ELITNÁ OHODNOCOVACIA MATEMATIKA ---
        calculateEliteScores(ls, weights);

        return ls;
    }

    private static double getEliteScore(double value, double safeLimit, double deathLimit) {
        double maxBonus = 1.10;
        if (safeLimit < deathLimit) {
            if (value >= deathLimit) return 0.01;
            if (value <= 0.0) return maxBonus;
            if (value <= safeLimit) {
                return 1.00 + (maxBonus - 1.00) * ((safeLimit - value) / safeLimit);
            } else {
                return 0.01 + 0.99 * ((deathLimit - value) / (deathLimit - safeLimit));
            }
        } else {
            if (value <= deathLimit) return 0.01;
            if (value >= 100.0) return maxBonus;
            if (value >= safeLimit) {
                return 1.00 + (maxBonus - 1.00) * ((value - safeLimit) / (100.0 - safeLimit));
            } else {
                return 0.01 + 0.99 * ((value - deathLimit) / (safeLimit - deathLimit));
            }
        }
    }

    private static void calculateEliteScores(FastLayoutStats ls, Weights weights) {
        double sfbScore = getEliteScore(ls.sfbPct, 1.5, 5.0);
        double sfsScore = getEliteScore(ls.sfsPct, 4.0, 9.0);
        double stretchScore = getEliteScore(ls.stretchPct, 6.0, 15.0);
        double scissorsScore = getEliteScore(ls.scissorsPct, 0.5, 3.0);
        double redirectsScore = getEliteScore(ls.redirectsPct, 4.0, 15.0);
        double lsbScore = getEliteScore(ls.lsbPct, 1.0, 6.0);
        double lhbScore = getEliteScore(ls.lhbPct, 1.0, 5.0);

        double altScore = getEliteScore(ls.altPct, 60.0, 40.0);
        double rollInScore = getEliteScore(ls.rollInPct, 15.0, 0.0);
        double rollOutScore = getEliteScore(ls.rollOutPct, 10.0, 0.0);
        double homeRowScore = getEliteScore(ls.homeRowPct, 65.0, 45.0);

        // 🔥 NOVINKA: Výpočet skóre pre jednoručné behy
        double runsScore = getEliteScore(ls.runsPct, 0.15, 1.20);

        // Limity pre prsty
        double f0 = getEliteScore(ls.fingerLoads[0], 9.0, 12.0);
        double f1 = getEliteScore(ls.fingerLoads[1], 20.0, 24.0);
        double f2 = getEliteScore(ls.fingerLoads[2], 22.0, 26.0);
        double f3 = getEliteScore(ls.fingerLoads[3], 26.0, 30.0);
        double f4 = getEliteScore(ls.fingerLoads[4], 26.0, 30.0);
        double f5 = getEliteScore(ls.fingerLoads[5], 22.0, 26.0);
        double f6 = getEliteScore(ls.fingerLoads[6], 20.0, 24.0);
        double f7 = getEliteScore(ls.fingerLoads[7], 9.0, 12.0);

        double maxFingerScore = Math.min(Math.min(Math.min(f0, f1), Math.min(f2, f3)), Math.min(Math.min(f4, f5), Math.min(f6, f7)));

        double totalWeight = weights.totalWeight();
        if (totalWeight == 0) return;

        double poweredProduct =
                Math.pow(sfbScore, weights.getSfb()) *
                        Math.pow(sfsScore, weights.getSfs()) *
                        Math.pow(stretchScore, weights.getStretch()) *
                        Math.pow(altScore, weights.getAlt()) *
                        Math.pow(scissorsScore, weights.getScissors()) *
                        Math.pow(redirectsScore, weights.getRedirects()) *
                        Math.pow(rollInScore, weights.getRollIn()) *
                        Math.pow(rollOutScore, weights.getRollOut()) *
                        Math.pow(maxFingerScore, weights.getFingerStats()) * Math.pow(lsbScore, weights.getLsb()) *
                        Math.pow(lhbScore, weights.getLhb()) *
                        Math.pow(homeRowScore, weights.getHomeRow()) *
                        Math.pow(runsScore, weights.getRuns()); // 🔥 OPRAVA: Pridané do finálneho násobenia telesa

        ls.eliteScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0;
    }

}
