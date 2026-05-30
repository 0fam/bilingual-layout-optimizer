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
    // Predalokované referencie pre bleskovú iteráciu prstov bez volania .values()
    private static final Finger[] FINGERS_CACHE = Finger.values();
    private static final int FINGER_COUNT = FINGERS_CACHE.length;

    private Evaluator() {}

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

        // =========================================================================
        // 🚀 HACK: Predpočítané multiplikátory pre všetky typy n-gramov
        // =========================================================================
        double mgMultiplier = (totalMonograms == 0) ? 0.0 : (100.0 / totalMonograms);
        double bgMultiplier = (totalBigrams == 0)  ? 0.0 : (100.0 / totalBigrams);
        double sgMultiplier = (totalSkipgrams == 0) ? 0.0 : (100.0 / totalSkipgrams);
        double tgMultiplier = (totalTrigrams == 0)  ? 0.0 : (100.0 / totalTrigrams);

        // =========================================================================
        // 🔴 BIGRAMY & SKIPGRAMY (Ploché lookupy bez bit-shiftov a vetvenia)
        // =========================================================================
        for (int i = 0; i < KEY_COUNT; i++) {
            char c1 = matrix[i];
            for (int j = 0; j < KEY_COUNT; j++) {
                char c2 = matrix[j];

                // A. SPRACOVANIE BIGRAMOV
                double bgFreq = ngStats.getBigramFreq(c1, c2);
                if (bgFreq > 0) {
                    if (KeyboardTopology.IS_ALT[i][j]) {
                        rawAlt += bgFreq;
                    } else {
                        if (KeyboardTopology.IS_SFB[i][j]) rawSfb += bgFreq;
                        if (KeyboardTopology.IS_SCISSORS[i][j]) rawScissors += bgFreq;
                        if (KeyboardTopology.IS_LSB[i][j]) rawLsb += bgFreq;
                        if (KeyboardTopology.IS_LHB[i][j]) rawLhb += bgFreq;
                    }
                }

                // B. SPRACOVANIE SKIPGRAMOV
                double sgFreq = ngStats.getSkipgramFreq(c1, c2);
                if (sgFreq > 0 && KeyboardTopology.IS_SFS[i][j]) {
                    rawSfs += sgFreq;
                }
            }
        }

        // =========================================================================
        // 🟡 TRIGRAMY (Ultra-rýchly 1D cyklus: spracováva iba 6750 validných pozícií)
        // =========================================================================
        for (int x = 0; x < KeyboardTopology.VALID_TRIPLES_COUNT; x++) {
            double tgFreq = ngStats.getTrigramFreq(
                    matrix[KeyboardTopology.TRIPLE_I[x]],
                    matrix[KeyboardTopology.TRIPLE_J[x]],
                    matrix[KeyboardTopology.TRIPLE_K[x]]
            );

            if (tgFreq <= 0) continue;

            // Typ pre-kompilovaného trigramu: 1 = ROLL_IN, 2 = ROLL_OUT, 3 = REDIRECT
            int type = KeyboardTopology.TRIPLE_TYPE[x];
            if (type == 1) rawRollIn += tgFreq;
            else if (type == 2) rawRollOut += tgFreq;
            else if (type == 3) rawRedirects += tgFreq;
        }

        LayoutStats result = new LayoutStats();

        // =========================================================================
        // 🟢 MONOGRAMY & PRSTY (Sčítanie bez delenia v horúcej slučke)
        // =========================================================================
        double[] localFingerPercents = new double[FINGER_COUNT];
        double rawHomeRow = 0;
        double rawStretch = 0;

        for (int i = 0; i < KEY_COUNT; i++) {
            double rawFreq = ngStats.getMonogramFreq(matrix[i]);
            double pct = rawFreq * mgMultiplier;

            result.setKeyPercent(i, pct);

            int info = KeyboardTopology.POS_INFO[i];
            localFingerPercents[info & 0x7] += pct;

            if (((info >> 7) & 0x3) == KeyboardTopology.HOME_ROW) rawHomeRow += rawFreq;
            rawStretch += rawFreq * KeyboardTopology.POS_STRETCH_WEIGHT[i];
        }

        for (int f = 0; f < FINGER_COUNT; f++) {
            result.setFingerPercent(f, localFingerPercents[f]);
        }

        // =========================================================================
        // 🧮 LINEÁRNY PREPOČET BEZ ALOKÁCIE POLÍ (Úplné vyradenie 'new double[]' a cyklu)
        // =========================================================================
        double totalProduct = 1.0;
        long totalCount = 0;

        // 1. SFB (Bigram -> použijeme bgMultiplier)
        double pctSfb = rawSfb * bgMultiplier; // ⚡ ŽIADNE DELENIE! Čisté rýchle násobenie
        result.setMetricPercent(MetricType.SFB, pctSfb);
        double coefSfb = weights.calculateCoefficient(MetricType.SFB, pctSfb);
        if (coefSfb >= 0) { totalProduct *= coefSfb; totalCount++; }

        // 2. SFS (Skipgram -> použijeme sgMultiplier)
        double pctSfs = rawSfs * sgMultiplier; // ⚡ Čisté násobenie
        result.setMetricPercent(MetricType.SFS, pctSfs);
        double coefSfs = weights.calculateCoefficient(MetricType.SFS, pctSfs);
        if (coefSfs >= 0) { totalProduct *= coefSfs; totalCount++; }

        // 3. ALTERNATION (Bigram -> bgMultiplier)
        double pctAlt = rawAlt * bgMultiplier;
        result.setMetricPercent(MetricType.ALTERNATION, pctAlt);
        double coefAlt = weights.calculateCoefficient(MetricType.ALTERNATION, pctAlt);
        if (coefAlt >= 0) { totalProduct *= coefAlt; totalCount++; }

        // 4. ROLL_IN (Trigram -> použijeme tgMultiplier)
        double pctRollIn = rawRollIn * tgMultiplier; // ⚡ Čisté násobenie
        result.setMetricPercent(MetricType.ROLL_IN, pctRollIn);
        double coefRollIn = weights.calculateCoefficient(MetricType.ROLL_IN, pctRollIn);
        if (coefRollIn >= 0) { totalProduct *= coefRollIn; totalCount++; }

        // 5. ROLL_OUT (Trigram -> tgMultiplier)
        double pctRollOut = rawRollOut * tgMultiplier;
        result.setMetricPercent(MetricType.ROLL_OUT, pctRollOut);
        double coefRollOut = weights.calculateCoefficient(MetricType.ROLL_OUT, pctRollOut);
        if (coefRollOut >= 0) { totalProduct *= coefRollOut; totalCount++; }

        // 6. SCISSORS (Bigram -> bgMultiplier)
        double pctScissors = rawScissors * bgMultiplier;
        result.setMetricPercent(MetricType.SCISSORS, pctScissors);
        double coefScissors = weights.calculateCoefficient(MetricType.SCISSORS, pctScissors);
        if (coefScissors >= 0) { totalProduct *= coefScissors; totalCount++; }

        // 7. LSB (Bigram -> bgMultiplier)
        double pctLsb = rawLsb * bgMultiplier;
        result.setMetricPercent(MetricType.LSB, pctLsb);
        double coefLsb = weights.calculateCoefficient(MetricType.LSB, pctLsb);
        if (coefLsb >= 0) { totalProduct *= coefLsb; totalCount++; }

        // 8. LHB (Bigram -> bgMultiplier)
        double pctLhb = rawLhb * bgMultiplier;
        result.setMetricPercent(MetricType.LHB, pctLhb);
        double coefLhb = weights.calculateCoefficient(MetricType.LHB, pctLhb);
        if (coefLhb >= 0) { totalProduct *= coefLhb; totalCount++; }

        // 9. REDIRECTS (Trigram -> tgMultiplier)
        double pctRedirects = rawRedirects * tgMultiplier;
        result.setMetricPercent(MetricType.REDIRECTS, pctRedirects);
        double coefRedirects = weights.calculateCoefficient(MetricType.REDIRECTS, pctRedirects);
        if (coefRedirects >= 0) { totalProduct *= coefRedirects; totalCount++; }

        // 10. HOME_ROW (Monogram -> mgMultiplier)
        double pctHome = rawHomeRow * mgMultiplier;
        result.setMetricPercent(MetricType.HOME_ROW, pctHome);
        double coefHome = weights.calculateCoefficient(MetricType.HOME_ROW, pctHome);
        if (coefHome >= 0) { totalProduct *= coefHome; totalCount++; }

        // 11. EFFORT (Monogram -> použijeme mgMultiplier)
        double pctEffort = rawStretch * mgMultiplier;
        result.setMetricPercent(MetricType.EFFORT, pctEffort);
        double coefEffort = weights.calculateCoefficient(MetricType.EFFORT, pctEffort);
        if (coefEffort >= 0) { totalProduct *= coefEffort; totalCount++; }

        // 12. PRSTY (Výpočet koeficientov cez statickú cache prstov)
        for (int i = 0; i < FINGER_COUNT; i++) {
            double coef = weights.calculateFingerCoefficient(FINGERS_CACHE[i], localFingerPercents[i]);
            if (coef >= 0) {
                totalProduct *= coef;
                totalCount++;
            }
        }

        // =========================================================================
        // 👑 FINÁLNE SKÓRE (Jediné Math.pow na úplnom konci výpočtu)
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
