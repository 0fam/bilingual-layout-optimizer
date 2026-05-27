package mh.layout.evaluator;

import mh.layout.stats.LayoutStats;
import mh.layout.stats.MetricType;
import mh.layout.stats.NGramStats;
import mh.layout.topology.KeyboardTopology;

public final class Evaluator {

    private static final int KEY_COUNT = KeyboardTopology.KEY_COUNT;

    private Evaluator() {}

    public static LayoutStats evaluate(char[] matrix, NGramStats nGramStats, Weights weights) {
        LayoutStats layoutStats = new LayoutStats(weights, nGramStats);

        double rawSfb = 0;
        double rawSfs = 0;
        double rawAlt = 0;
        double rawRollIn = 0;
        double rawRollOut = 0;
        double rawScissors = 0;
        double rawLsb = 0;
        double rawLhb = 0;
        double rawRedirects = 0;

        // Vytiahneme si bitové pole do CPU registra metódy
        final int[] posInfo = KeyboardTopology.POS_INFO;

        // =========================================================================
        // 🟢 1. MONOGRAMY: Iba zber surových dát (Tvoj skvelý nápad!)
        // =========================================================================
        for (int i = 0; i < KEY_COUNT; i++) {
            layoutStats.setKeyFrequency(i, nGramStats.getMonogramFreq(matrix[i]));
        }

        // =========================================================================
        // 🔴 2. BIGRAMY & SKIPGRAMY (30 x 30 = 900 prechodov)
        // =========================================================================
        for (int i = 0; i < KEY_COUNT; i++) {
            char c1 = matrix[i];
            int info1 = posInfo[i]; // Všetko o klávese 1 v jednom čísle

            // Vybalíme anatómiu klávesu 1 pomocou bleskových bitových masiek
            int hand1   = (info1 >> 9) & 0x1;
            int row1    = (info1 >> 7) & 0x3;
            int col1    = (info1 >> 3) & 0xF;
            int finger1 = info1 & 0x7;

            for (int j = 0; j < KEY_COUNT; j++) {
                char c2 = matrix[j];
                int info2 = posInfo[j];

                int hand2   = (info2 >> 9) & 0x1;
                int row2    = (info2 >> 7) & 0x3;
                int col2    = (info2 >> 3) & 0xF;
                int finger2 = info2 & 0x7;

                // A. SPRACOVANIE BIGRAMOV
                double bgFreq = nGramStats.getBigramFreq(c1, c2);
                if (bgFreq > 0) {

                    if (hand1 != hand2) {
                        rawAlt += bgFreq; // Striedanie rúk
                    } else {
                        // Sme na rovnakej ruke

                        if (finger1 == finger2 && i != j) {
                            rawSfb += bgFreq; // SFB (Rovnaký prst)
                        }

                        // SCISSORS (Nožnice: skok cez riadok na susedných prstoch)
                        if (Math.abs(row1 - row2) >= 2 && finger1 != finger2) {
                            rawScissors += bgFreq;
                        }

                        // LSB (Lateral Stretch: zásah do stredových stĺpcov 4 alebo 5 ukazovákmi 3 alebo 4)
                        if ((col1 == 4 || col1 == 5 || col2 == 4 || col2 == 5) && (finger1 == 3 || finger1 == 4)) {
                            rawLsb += bgFreq;
                        }

                        // LHB (Lateral Hand Bigram: únava malíček [0,7] + prstenník [1,6])
                        if ((finger1 == 0 && finger2 == 1) || (finger1 == 7 && finger2 == 6)) {
                            rawLhb += bgFreq;
                        }
                    }
                }

                // B. SPRACOVANIE SKIPGRAMOV (Sendviče - SFS)
                double sgFreq = nGramStats.getSkipgramFreq(c1, c2);
                if (sgFreq > 0 && hand1 == hand2 && finger1 == finger2 && i != j) {
                    rawSfs += sgFreq;
                }
            }
        }

        // =========================================================================
        // 🟡 3. TRIGRAMY (27 000 prechodov) - Valenie a Otočky
        // =========================================================================
        for (int i = 0; i < KEY_COUNT; i++) {
            char c1 = matrix[i];
            int info1 = posInfo[i];
            int hand1 = (info1 >> 9) & 0x1;
            int f1    = info1 & 0x7;

            for (int j = 0; j < KEY_COUNT; j++) {
                char c2 = matrix[j];
                int info2 = posInfo[j];
                int hand2 = (info2 >> 9) & 0x1;
                int f2    = info2 & 0x7;

                // Ak prvé dva klávesy nie sú na rovnakej ruke, jednoručný trigram to už nebude
                if (hand1 != hand2) continue;

                for (int k = 0; k < KEY_COUNT; k++) {
                    char c3 = matrix[k];
                    int info3 = posInfo[k];
                    int hand3 = (info3 >> 9) & 0x1;
                    int f3    = info3 & 0x7;

                    if (hand2 != hand3) continue; // Celý trigram musí byť na jednej ruke

                    double tgFreq = nGramStats.getTrigramFreq(c1, c2, c3);
                    if (tgFreq <= 0) continue;

                    // Smer valenia určujeme čistým porovnaním ID prstov (0 až 7 zľava doprava)
                    if (f1 < f2 && f2 < f3) {
                        if (hand1 == KeyboardTopology.LEFT_HAND) rawRollOut += tgFreq;
                        else rawRollIn += tgFreq;
                    }
                    else if (f1 > f2 && f2 > f3) {
                        if (hand1 == KeyboardTopology.LEFT_HAND) rawRollIn += tgFreq;
                        else rawRollOut += tgFreq;
                    }
                    // REDIRECTS (Otočky)
                    else if ((f1 < f2 && f2 > f3) || (f1 > f2 && f2 < f3)) {
                        if (f1 != f2 && f2 != f3 && f1 != f3) {
                            rawRedirects += tgFreq;
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 📦 4. ZASYPANIE N-GRAMOVÝCH DÁT DO SCHRÁNKY
        // =========================================================================
        layoutStats.set(MetricType.SFB,         rawSfb);
        layoutStats.set(MetricType.SFS,         rawSfs);
        layoutStats.set(MetricType.ALTERNATION, rawAlt);
        layoutStats.set(MetricType.ROLL_IN,     rawRollIn);
        layoutStats.set(MetricType.ROLL_OUT,    rawRollOut);
        layoutStats.set(MetricType.SCISSORS,    rawScissors);
        layoutStats.set(MetricType.LSB,         rawLsb);
        layoutStats.set(MetricType.LHB,         rawLhb);
        layoutStats.set(MetricType.REDIRECTS,   rawRedirects);

        return layoutStats;
    }
}
