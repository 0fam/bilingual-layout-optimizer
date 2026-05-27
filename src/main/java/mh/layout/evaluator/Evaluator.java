package mh.layout.evaluator;

import mh.layout.stats.LayoutStats;
import mh.layout.stats.MetricType;
import mh.layout.stats.NGramStats;
import mh.layout.topology.Finger;
import mh.layout.topology.KeyboardTopology;

public final class Evaluator {

    private static final int KEY_COUNT = KeyboardTopology.KEY_COUNT;

    // 🚀 ULTRA-OPTIMALIZÁCIA: Statické cache pre horúcu slučku
    private static final MetricType[] METRICS_CACHE = MetricType.values();
    private static final Finger[] FINGERS_CACHE = Finger.values();
    private static final int FINGER_COUNT = FINGERS_CACHE.length;

    private Evaluator() {}

    // 🚀 SIGNATÚRA PRIJÍMA PREDPOČÍTANÉ MENOVATELE - ŽIADNY ZBER V HORÚCEJ SLUČKE
    public static LayoutStats evaluate(char[] matrix, NGramStats ngStats, Weights weights,
                                       double totalMonograms, double totalBigrams,
                                       double totalSkipgrams, double totalTrigrams) {
        double rawSfb = 0;
        double rawSfs = 0;
        double rawAlt = 0;
        double rawRollIn = 0;
        double rawRollOut = 0;
        double rawScissors = 0;
        double rawLsb = 0;
        double rawLhb = 0;
        double rawRedirects = 0;

        final int[] posInfo = KeyboardTopology.POS_INFO;

        // =========================================================================
        // 🔴 BIGRAMY & SKIPGRAMY (30 x 30 = 900 prechodov)
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
                double bgFreq = ngStats.getBigramFreq(c1, c2);
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
                        if (col1 == 4 || col1 == 5 || col2 == 4 || col2 == 5) {
                            rawLsb += bgFreq;
                        }

                        // LHB (Lateral Hand Bigram: únava malíček [0,7] + prstenník [1,6])
                        if ((finger1 == 0 && finger2 == 1) || (finger1 == 1 && finger2 == 0) ||
                                (finger1 == 7 && finger2 == 6) || (finger1 == 6 && finger2 == 7)) {
                            rawLhb += bgFreq;
                        }
                    }
                }

                // B. SPRACOVANIE SKIPGRAMOV (Sendviče - SFS)
                double sgFreq = ngStats.getSkipgramFreq(c1, c2);
                if (sgFreq > 0 && hand1 == hand2 && finger1 == finger2 && i != j) {
                    rawSfs += sgFreq;
                }
            }
        }

        // =========================================================================
        // 🟡 TRIGRAMY - S NÁVRATOM AGRESÍVNYCH EARLY EXITS!
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

                // 🚀 SKORÁ BRZDA 1: Ak prvé dva klávesy nie sú na rovnakej ruke, ruka sa strieda -> koniec
                if (hand1 != hand2) continue;

                for (int k = 0; k < KEY_COUNT; k++) {
                    char c3 = matrix[k];
                    int info3 = posInfo[k];
                    int hand3 = (info3 >> 9) & 0x1;
                    int f3    = info3 & 0x7;

                    // 🚀 SKORÁ BRZDA 2: Celý trigram musí byť na jednej ruke
                    if (hand2 != hand3) continue;

                    double tgFreq = ngStats.getTrigramFreq(c1, c2, c3);
                    if (tgFreq <= 0) continue;

                    if (f1 < f2 && f2 < f3) {
                        if (hand1 == KeyboardTopology.LEFT_HAND) rawRollIn += tgFreq;
                        else rawRollOut += tgFreq;
                    }
                    else if (f1 > f2 && f2 > f3) {
                        if (hand1 == KeyboardTopology.LEFT_HAND) rawRollOut += tgFreq;
                        else rawRollIn += tgFreq;
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

        LayoutStats result = new LayoutStats();

        // =========================================================================
        // 🟢 MONOGRAMY & PRSTY
        // =========================================================================
        double[] localFingerPercents = new double[FINGER_COUNT];
        double rawHomeRow = 0;
        double rawStretch = 0;

        for (int i = 0; i < KEY_COUNT; i++) {
            double rawFreq = ngStats.getMonogramFreq(matrix[i]);
            double pct = (totalMonograms == 0) ? 0.0 : (rawFreq * 100.0) / totalMonograms;

            result.setKeyPercent(i, pct);

            int info = posInfo[i];
            localFingerPercents[info & 0x7] += pct;

            if (((info >> 7) & 0x3) == KeyboardTopology.HOME_ROW) rawHomeRow += rawFreq;
            rawStretch += rawFreq * KeyboardTopology.POS_STRETCH_WEIGHT[i];
        }

        // 🚀 OPTIMALIZOVANÉ: Prsty plníme cez lokálnu cache, žiadne klonovanie poľa
        for (int f = 0; f < FINGER_COUNT; f++) {
            result.setFingerPercent(f, localFingerPercents[f]);
        }

        // =========================================================================
        // 🧮 PREPOČET METRÍK NA PERCENTÁ VIAZANÝ NA PREDPOČÍTANÉ KONŠTANTY
        // =========================================================================
        double totalProduct = 1.0;
        long totalCount = 0;

        double[] raws = new double[METRICS_CACHE.length];
        raws[MetricType.SFB.ordinal()] = rawSfb;
        raws[MetricType.SFS.ordinal()] = rawSfs;
        raws[MetricType.ALTERNATION.ordinal()] = rawAlt;
        raws[MetricType.ROLL_IN.ordinal()] = rawRollIn;
        raws[MetricType.ROLL_OUT.ordinal()] = rawRollOut;
        raws[MetricType.SCISSORS.ordinal()] = rawScissors;
        raws[MetricType.LSB.ordinal()] = rawLsb;
        raws[MetricType.LHB.ordinal()] = rawLhb;
        raws[MetricType.REDIRECTS.ordinal()] = rawRedirects;
        raws[MetricType.HOME_ROW.ordinal()] = rawHomeRow;
        raws[MetricType.EFFORT.ordinal()] = rawStretch;

        for (int i = 0; i < METRICS_CACHE.length; i++) {
            MetricType type = METRICS_CACHE[i];

            double denominator = switch (type) {
                case HOME_ROW, EFFORT, RUNS -> totalMonograms;
                case SFB, ALTERNATION, SCISSORS, LSB, LHB -> totalBigrams;
                case SFS -> totalSkipgrams;
                case REDIRECTS, ROLL_IN, ROLL_OUT -> totalTrigrams;
            };

            double pct = (denominator == 0) ? 0.0 : (raws[i] * 100.0) / denominator;
            result.setMetricPercent(type, pct);

            double coef = weights.calculateCoefficient(type, pct);
            if (coef >= 0) {
                totalProduct *= coef;
                totalCount++;
            }
        }

        // 🚀 OPTIMALIZOVANÉ: Výpočet prstových koeficientov cez statickú cache
        for (int i = 0; i < FINGER_COUNT; i++) {
            Finger f = FINGERS_CACHE[i];
            double coef = weights.calculateFingerCoefficient(f, localFingerPercents[i]);
            if (coef >= 0) {
                totalProduct *= coef;
                totalCount++;
            }
        }

        // =========================================================================
        // 👑 4. FINÁLNE SKÓRE TELESA (Jediné Pow na samom konci)
        // =========================================================================
        if (totalCount > 0) {
            result.setScore(Math.pow(totalProduct, 1.0 / totalCount) * 100.0);
        }

        return result;
    }

    // Tento overloading pridaj do Evaluator.java
    public static LayoutStats evaluate(char[] matrix, NGramStats ngStats, Weights weights) {
        double mono = 0, bigram = 0, skip = 0, tri = 0;

        // Interne si spočíta menovatele na počkanie pre standalone analýzy
        for (int i = 0; i < 30; i++) {
            mono += ngStats.getMonogramFreq(matrix[i]);
            for (int j = 0; j < 30; j++) {
                bigram += ngStats.getBigramFreq(matrix[i], matrix[j]);
                skip   += ngStats.getSkipgramFreq(matrix[i], matrix[j]);
                for (int k = 0; k < 30; k++) {
                    tri += ngStats.getTrigramFreq(matrix[i], matrix[j], matrix[k]);
                }
            }
        }
        // Deleguje prácu na hlavné bleskové jadro
        return evaluate(matrix, ngStats, weights, mono, bigram, skip, tri);
    }

}
