package mh.layout.core.evaluator;

import mh.layout.core.stats.LayoutStats;
import mh.layout.core.stats.NGramStats;
import mh.layout.core.topology.KeyboardTopology;

public final class Evaluator {

    private Evaluator() {}

    public static LayoutStats evaluate(char[] matrix, NGramStats stats, Weights weights) {

        // --- INICIALIZÁCIA METRÍK ---
        double stretchScore = 0;
        double homeRowScore = 0;
        double fingerLoadScore = 0;

        double sfbScore = 0;
        double sfsScore = 0;
        double scissorsScore = 0;
        double lsbScore = 0;
        double lhbScore = 0;
        double altScore = 0;
        double rollInScore = 0;
        double rollOutScore = 0;

        double redirectsScore = 0;

        // Pomocné pole pre priebežnú akumuláciu surovej záťaže prstov
        double[] fingerUsage = new double[8];


        // =========================================================================
        // 🎯 1. MONOGRAMY: Statický komfort pozícií a vyťaženie prstov
        // =========================================================================
        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            double freq = stats.getMonogramFreq(c);
            if (freq == 0) continue;

            // Výpočet mechanického naťahovania prstov (Stretch) podľa topológie
            stretchScore += freq * KeyboardTopology.POS_STRETCH_WEIGHT[i];

            // Sčítavanie odmeny za udržanie frekventovaných klávesov na domovskom riadku
            if (KeyboardTopology.POS_TO_ROW[i] == KeyboardTopology.HOME_ROW) {
                homeRowScore += freq;
            }

            // Akumulácia celkového objemu práce pre konkrétny prst
            fingerUsage[KeyboardTopology.POS_TO_FINGER[i]] += freq;
        }


        // =========================================================================
        // ⚖️ 2. ANATOMICKÁ POISTKA: Výpočet špičkového zaťaženia prstov (Finger Load)
        // =========================================================================
        for (int f = 0; f < 8; f++) {
            double usage = fingerUsage[f];
            double penaltyFactor = 1.0;

            // Váhové koeficienty: Malíčky a prstenníky unavíš skôr ako silné ukazováky
            if (f == KeyboardTopology.L_PINKY  || f == KeyboardTopology.R_PINKY)  penaltyFactor = 2.0;
            if (f == KeyboardTopology.L_RING   || f == KeyboardTopology.R_RING)   penaltyFactor = 1.5;
            if (f == KeyboardTopology.L_MIDDLE || f == KeyboardTopology.R_MIDDLE) penaltyFactor = 1.1;

            double calculatedLoad = usage * penaltyFactor;
            if (calculatedLoad > fingerLoadScore) {
                fingerLoadScore = calculatedLoad; // Hľadáme najviac preťažený vrchol (Peak stress)
            }
        }


        // =========================================================================
        // 🧮 3. BIGRAMY A SKIPGRAMY: Maticový prechod klávesnice 30x30
        // =========================================================================
        for (int i = 0; i < 30; i++) {
            char c1 = matrix[i];
            int hand1   = KeyboardTopology.POS_TO_HAND[i];
            int row1    = KeyboardTopology.POS_TO_ROW[i];
            int finger1 = KeyboardTopology.POS_TO_FINGER[i];

            for (int j = 0; j < 30; j++) {
                if (i == j) continue;

                char c2 = matrix[j];
                double bFreq = stats.getBigramFreq(c1, c2);
                double sFreq = stats.getSkipgramFreq(c1, c2);

                // Ak táto dvojica znakov nemá v korpuse žiadnu frekvenciu, preskakujeme
                if (bFreq == 0 && sFreq == 0) continue;

                int hand2   = KeyboardTopology.POS_TO_HAND[j];
                int row2    = KeyboardTopology.POS_TO_ROW[j];
                int finger2 = KeyboardTopology.POS_TO_FINGER[j];

                // --- A. JEDNORUČNÉ METRIKY (Rovnaká ruka) ---
                if (hand1 == hand2) {

                    // SFB & SFS: Údery po sebe alebo cez jeden znak (sendvič) na tom istom prste
                    if (finger1 == finger2) {
                        if (bFreq > 0) sfbScore += bFreq;
                        if (sFreq > 0) sfsScore += sFreq;
                    }
                    else {
                        // SCISSORS: Susedné prsty, kde jeden letí hore a druhý sa krčí dole (rozdiel riadkov = 2)
                        if (bFreq > 0 && Math.abs(finger1 - finger2) == 1 && Math.abs(row1 - row2) == 2) {
                            scissorsScore += bFreq;
                        }

                        // LHB: Simultánny tlak a únava na kritickú dvojicu malíček + prstenník
                        if (bFreq > 0 && ((finger1 == KeyboardTopology.L_PINKY && finger2 == KeyboardTopology.L_RING) ||
                                (finger1 == KeyboardTopology.L_RING   && finger2 == KeyboardTopology.L_PINKY) ||
                                (finger1 == KeyboardTopology.R_PINKY && finger2 == KeyboardTopology.R_RING) ||
                                (finger1 == KeyboardTopology.R_RING   && finger2 == KeyboardTopology.R_PINKY))) {
                            lhbScore += bFreq;
                        }

                        // VALENIE PRSTOV (Rolls): Smerový tok pohybu prstov dnu k palcu (In) alebo von k malíčku (Out)
                        if (bFreq > 0) {
                            if (hand1 == KeyboardTopology.LEFT_HAND) {
                                if (finger1 < finger2) rollInScore += bFreq;
                                else                   rollOutScore += bFreq;
                            } else {
                                if (finger1 > finger2) rollInScore += bFreq;
                                else                   rollOutScore += bFreq;
                            }
                        }
                    }
                }
                // --- B. OBOJRUČNÉ METRIKY (Ruky sa striedajú) ---
                else {
                    if (bFreq > 0) altScore += bFreq; // Čistá rytmická alternácia ľavá-pravá
                }

                // --- C. BOČNÉ STREČE (LSB) ---
                // Sleduje, či bigram zaťažuje vnútorné stĺpce ukazovákov (indexy 4, 14, 24 a 5, 15, 25)
                if (bFreq > 0 && (i == 4 || i == 14 || i == 24 || i == 5 || i == 15 || i == 25 ||
                        j == 4 || j == 14 || j == 24 || j == 5 || j == 15 || j == 25)) {
                    lsbScore += bFreq;
                }
            }
        }


        // =========================================================================
        // 🔄 4. TRIGRAMY: Smerové zmeny (Redirects / Pinbally) 30x30x30
        // =========================================================================
        for (int i = 0; i < 30; i++) {
            char c1 = matrix[i];
            int hand1   = KeyboardTopology.POS_TO_HAND[i];
            int finger1 = KeyboardTopology.POS_TO_FINGER[i];

            for (int j = 0; j < 30; j++) {
                if (i == j) continue;
                char c2 = matrix[j];
                int hand2   = KeyboardTopology.POS_TO_HAND[j];
                int finger2 = KeyboardTopology.POS_TO_FINGER[j];

                // Ak prvé dva údery nie sú na jednej ruke, čistý jednoručný trigram to už byť nemôže
                if (hand1 != hand2) continue;

                for (int k = 0; k < 30; k++) {
                    if (j == k || i == k) continue;
                    char c3 = matrix[k];

                    double trigramFreq = stats.getTrigramFreq(c1, c2, c3);
                    if (trigramFreq == 0) continue;

                    int hand3   = KeyboardTopology.POS_TO_HAND[k];
                    int finger3 = KeyboardTopology.POS_TO_FINGER[k];

                    // Ak sa smer pohybu prstov medzi údermi zlomil (napr. prstenník -> ukazovák -> prostredník)
                    if (hand2 == hand3) {
                        if ((finger1 < finger2 && finger2 > finger3) ||
                                (finger1 > finger2 && finger2 < finger3)) {
                            redirectsScore += trigramFreq;
                        }
                    }
                }
            }
        }


        // =========================================================================
        // 🏆 5. FINÁLNE ZÚČTOVANIE: Výpočet bilingválneho ELITE skóre
        // =========================================================================

        // Pozitívne biomechanické vlastnosti (Odmeny pripočítavame)
        double rewards = (homeRowScore * weights.getHomeRow())
                + (altScore     * weights.getAlt())
                + (rollInScore  * weights.getRollIn())
                + (rollOutScore * weights.getRollOut());

        // Negatívne biomechanické vlastnosti (Tresty odpočítavame)
        double penalties = (stretchScore   * weights.getStretch())
                + (fingerLoadScore * weights.getFingerLoad())
                + (sfbScore       * weights.getSfb())
                + (sfsScore       * weights.getSfs())
                + (scissorsScore  * weights.getScissors())
                + (redirectsScore * weights.getRedirects())
                + (lsbScore       * weights.getLsb())
                + (lhbScore       * weights.getLhb());

        double finalEliteScore = rewards - penalties;

        // Zabalenie kompletných výsledkov do prepravného kontajnera
        return new LayoutStats(
                finalEliteScore, sfbScore, sfsScore, stretchScore, scissorsScore, redirectsScore,
                fingerLoadScore, lsbScore, lhbScore, homeRowScore, altScore, rollInScore, rollOutScore
        );
    }
}
