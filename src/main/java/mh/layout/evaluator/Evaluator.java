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
                                       double mgMultiplier, double bgMultiplier,
                                       double sgMultiplier, double tgMultiplier) {
        double rawASfbDown = 0;
        double rawASfbUp = 0;
        double rawRSSfb = 0;
        double rawSSfb = 0;
        double rawSft = 0; // Same Finger Trigram
        double rawSfs = 0;
        double rawAlt = 0;
        double rawRollIn = 0;
        double rawRollOut = 0;
        double rawScissors = 0;
        double rawLsb = 0;
        double rawLhb = 0;
        double rawRedirects = 0;

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
                        if (KeyboardTopology.IS_ASFB_DOWN[i][j]) rawASfbDown += bgFreq;
                        if (KeyboardTopology.IS_ASFB_UP[i][j]) rawASfbUp += bgFreq;
                        if (KeyboardTopology.IS_SSFB[i][j]) rawSSfb += bgFreq;
                        if (KeyboardTopology.IS_RSSFB[i][j]) rawRSSfb += bgFreq;
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
            else if (type == 4) rawSft += tgFreq;
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
        double pctASfbDown = rawASfbDown * bgMultiplier; // ⚡ ŽIADNE DELENIE! Čisté rýchle násobenie
        result.setMetricPercent(MetricType.ASFBD, pctASfbDown);
        double coefSfbDown = weights.calculateCoefficient(MetricType.ASFBD, pctASfbDown);
        result.setMetricCoef(MetricType.ASFBD, coefSfbDown);
        if (coefSfbDown >= 0) { totalProduct *= coefSfbDown; totalCount++; }

        double pctASfbUp = rawASfbUp * bgMultiplier; // ⚡ ŽIADNE DELENIE! Čisté rýchle násobenie
        result.setMetricPercent(MetricType.ASFBU, pctASfbUp);
        double coefASfbUp = weights.calculateCoefficient(MetricType.ASFBU, pctASfbUp);
        result.setMetricCoef(MetricType.ASFBU, coefASfbUp);
        if (coefASfbUp >= 0) { totalProduct *= coefASfbUp; totalCount++; }

        double pctRSSfb = rawRSSfb * bgMultiplier; // ⚡ ŽIADNE DELENIE! Čisté rýchle násobenie
        result.setMetricPercent(MetricType.RSSFB, pctRSSfb);
        double coefRSSfb = weights.calculateCoefficient(MetricType.RSSFB, pctRSSfb);
        result.setMetricCoef(MetricType.RSSFB, coefRSSfb);
        if (coefRSSfb >= 0) { totalProduct *= coefRSSfb; totalCount++; }

        double pctSSfb = rawSSfb * bgMultiplier; // ⚡ ŽIADNE DELENIE! Čisté rýchle násobenie
        result.setMetricPercent(MetricType.SSFB, pctSSfb);
        double coefSSfb = weights.calculateCoefficient(MetricType.SSFB, pctSSfb);
        result.setMetricCoef(MetricType.SSFB, coefSSfb);
        if (coefSSfb >= 0) { totalProduct *= coefSSfb; totalCount++; }

        double pctSfT = rawSft * tgMultiplier; // ⚡ ŽIADNE DELENIE! Čisté rýchle násobenie
        result.setMetricPercent(MetricType.SFT, pctSfT);
        double coefSfT = weights.calculateCoefficient(MetricType.SFT, pctSfT);
        result.setMetricCoef(MetricType.SFT, coefSfT);
        if (coefSfT >= 0) { totalProduct *= coefSfT; totalCount++; }

        // 2. SFS (Skipgram -> použijeme sgMultiplier)
        double pctSfs = rawSfs * sgMultiplier; // ⚡ Čisté násobenie
        result.setMetricPercent(MetricType.SFS, pctSfs);
        double coefSfs = weights.calculateCoefficient(MetricType.SFS, pctSfs);
        result.setMetricCoef(MetricType.SFS, coefSfs);
        if (coefSfs >= 0) { totalProduct *= coefSfs; totalCount++; }

        // 3. ALTERNATION (Bigram -> bgMultiplier)
        double pctAlt = rawAlt * bgMultiplier;
        result.setMetricPercent(MetricType.ALTERNATION, pctAlt);
        double coefAlt = weights.calculateCoefficient(MetricType.ALTERNATION, pctAlt);
        result.setMetricCoef(MetricType.ALTERNATION, coefAlt);
        if (coefAlt >= 0) { totalProduct *= coefAlt; totalCount++; }

        // 4. ROLL_IN (Trigram -> použijeme tgMultiplier)
        double pctRollIn = rawRollIn * tgMultiplier; // ⚡ Čisté násobenie
        result.setMetricPercent(MetricType.ROLL_IN, pctRollIn);
        double coefRollIn = weights.calculateCoefficient(MetricType.ROLL_IN, pctRollIn);
        result.setMetricCoef(MetricType.ROLL_IN, coefRollIn);
        if (coefRollIn >= 0) { totalProduct *= coefRollIn; totalCount++; }

        // 5. ROLL_OUT (Trigram -> tgMultiplier)
        double pctRollOut = rawRollOut * tgMultiplier;
        result.setMetricPercent(MetricType.ROLL_OUT, pctRollOut);
        double coefRollOut = weights.calculateCoefficient(MetricType.ROLL_OUT, pctRollOut);
        result.setMetricCoef(MetricType.ROLL_OUT, coefRollOut);
        if (coefRollOut >= 0) { totalProduct *= coefRollOut; totalCount++; }

        // 6. SCISSORS (Bigram -> bgMultiplier)
        double pctScissors = rawScissors * bgMultiplier;
        result.setMetricPercent(MetricType.SCISSORS, pctScissors);
        double coefScissors = weights.calculateCoefficient(MetricType.SCISSORS, pctScissors);
        result.setMetricCoef(MetricType.SCISSORS, coefScissors);
        if (coefScissors >= 0) { totalProduct *= coefScissors; totalCount++; }

        // 7. LSB (Bigram -> bgMultiplier)
        double pctLsb = rawLsb * bgMultiplier;
        result.setMetricPercent(MetricType.LSB, pctLsb);
        double coefLsb = weights.calculateCoefficient(MetricType.LSB, pctLsb);
        result.setMetricCoef(MetricType.LSB, coefLsb);
        if (coefLsb >= 0) { totalProduct *= coefLsb; totalCount++; }

        // 8. LHB (Bigram -> bgMultiplier)
        double pctLhb = rawLhb * bgMultiplier;
        result.setMetricPercent(MetricType.LHB, pctLhb);
        double coefLhb = weights.calculateCoefficient(MetricType.LHB, pctLhb);
        result.setMetricCoef(MetricType.LHB, coefLhb);
        if (coefLhb >= 0) { totalProduct *= coefLhb; totalCount++; }

        // 9. REDIRECTS (Trigram -> tgMultiplier)
        double pctRedirects = rawRedirects * tgMultiplier;
        result.setMetricPercent(MetricType.REDIRECTS, pctRedirects);
        double coefRedirects = weights.calculateCoefficient(MetricType.REDIRECTS, pctRedirects);
        result.setMetricCoef(MetricType.REDIRECTS, coefRedirects);
        if (coefRedirects >= 0) { totalProduct *= coefRedirects; totalCount++; }

        // 10. HOME_ROW (Monogram -> mgMultiplier)
        double pctHome = rawHomeRow * mgMultiplier;
        result.setMetricPercent(MetricType.HOME_ROW, pctHome);
        double coefHome = weights.calculateCoefficient(MetricType.HOME_ROW, pctHome);
        result.setMetricCoef(MetricType.HOME_ROW, coefHome);
        if (coefHome >= 0) { totalProduct *= coefHome; totalCount++; }

        // 11. EFFORT (Monogram -> použijeme mgMultiplier)
        double pctEffort = rawStretch * mgMultiplier;
        result.setMetricPercent(MetricType.EFFORT, pctEffort);
        double coefEffort = weights.calculateCoefficient(MetricType.EFFORT, pctEffort);
        result.setMetricCoef(MetricType.EFFORT, coefEffort);
        if (coefEffort >= 0) { totalProduct *= coefEffort; totalCount++; }

        // 12. PRSTY (Výpočet koeficientov cez statickú cache prstov)
        for (int i = 0; i < FINGER_COUNT; i++) {
            double coef = weights.calculateFingerCoefficient(FINGERS_CACHE[i], localFingerPercents[i]);
            result.setFingerCoef(i, coef);
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

        NGramsMultipliers multi = NGramsMultipliers.compute(matrix, ngStats);

        // Deleguje prácu na hlavné bleskové jadro
        return evaluate(matrix, ngStats, weights, multi.monoX(), multi.biX(), multi.skipX(), multi.triX());
    }

}
