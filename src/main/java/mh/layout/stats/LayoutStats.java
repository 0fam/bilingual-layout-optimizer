package mh.layout.stats;

import java.util.Arrays;

public final class LayoutStats {
    private static final int METRIC_COUNT = MetricType.values().length;

    private final Weights weights;
    private final NGramStats nGramStats;

    // 👑 CELKOVÝ VÝSLEDOK
    private double totalScore = -1;

    // 📊 VŠETKY METRIKY V JEDNOM PLOCHOM POĽA (Veľkosť 20 podľa MetricType)
    private final double[] metrics = new double[METRIC_COUNT];
    private final double[] metricPercents = new double[METRIC_COUNT];
    private final double[] metricCoefficients = new double[METRIC_COUNT];

    // 📊 SUROVÉ FREKVENCIE A PERCENTÁ PRE KAŽDÝ Z 30 KLÁVESOV TOTEMU
    private final double[] keyFrequencies = new double[30];
    private final double[] keyPercents = new double[30];

    public LayoutStats(Weights weights, NGramStats nGramStats) {
        this.weights = weights;
        this.nGramStats = nGramStats;
        // Inicializujeme celé pole na -1, presne ako v tvojom pôvodnom kóde
        Arrays.fill(metrics, -1.0);
        Arrays.fill(metricPercents, -1.0);
        Arrays.fill(metricCoefficients, -1);
    }

    // =========================================================================
    // ⚡ BLESKOVÝ PRÍSTUP CEZ ORDINAL ENUMU (O(1) lookup bez réžie)
    // =========================================================================

    public double get(MetricType type) {
        return metrics[type.ordinal()];
    }

    public void set(MetricType type, double value) {
        this.metrics[type.ordinal()] = value;
    }

    /**
     * Setter, ktorým Evaluator v horúcej slučke bleskovo zapíše surovú frekvenciu pozície.
     */
    public void setKeyFrequency(int pos, double freq) {
        this.keyFrequencies[pos] = freq;
    }

    /**
     * Getter, z ktorého neskôr LayoutAnalyzer vytiahne presné % pre vizuálnu heat-mapu.
     */
    public double getKeyPercent(int pos) {
        return keyPercents[pos];
    }

    // =========================================================================
    // ⚙️ LOGIKA VÝPOČTU A KONTROLY
    // =========================================================================

    public double getScore() {
        if (totalScore < 0) {
            totalScore = computeScore();
        }
        return totalScore;
    }

    private double computeScore() {

        computePercents();

        computeCoefficients();

        double product = Arrays.stream(metricCoefficients).filter(value -> value >= 0).reduce(1.0, (a, b) -> a * b);
        long count = Arrays.stream(metrics).filter(value -> value >= 0).count();

        if (count == 0) return 0.0;

        return totalScore = Math.pow(product, 1.0 / count) * 100.0; // toto je priemer n-rozmerneho telesa, namiesto objemu
//        return globalScore = poweredProduct * 10000; // ak preferujes hrube skore, pozor, vysledky budu logaritmickeho charakteru

    }

    /**
     * KĽÚČOVÉ DOPLNENIE: Preráta surové sumy na reálne percentá s ochranou proti deleniu nulou.
     */
    private void computePercents() {
        for (int i = 0; i < METRIC_COUNT; i++) {
            MetricType type = MetricType.values()[i];
            double rawValue = metrics[i];

            // Ak optimizér túto metriku vôbec nezmeral, necháme predvolenú hodnotu
            if (rawValue < 0) {
                continue;
            }

            // Nový, moderný switch expression priradí správneho menovateľa pre všetkých 20 metrík
            long denominator = switch (type) {
                case HOME_ROW, STRETCH, RUNS,
                     L_PINKY, L_RING, L_MIDDLE, L_INDEX,
                     R_INDEX, R_MIDDLE, R_RING, R_PINKY -> nGramStats.getTotalMonogramsCount();

                case SFB, ALTERNATION, SCISSORS, LSB, LHB -> nGramStats.getTotalBigramsCount();
                case SFS -> nGramStats.getTotalSkipgramsCount();
                case REDIRECTS, ROLL_IN, ROLL_OUT -> nGramStats.getTotalTrigramsCount();
            };

            // Výpočet percent s poistkou proti deleniu nulou
            metricPercents[i] = (denominator == 0) ? 0.0 : (rawValue * 100.0) / denominator;
        }
    }

    private void computeCoefficients() {
        for (int i = 0; i < METRIC_COUNT; i++) {
            MetricType type = MetricType.values()[i];
            metricCoefficients[i] = weights.calculateCoefficient(type, metricPercents[i]);
        }
    }

    /**
     * Skontroluje, či optimizér korektne naplnil úplne všetky namerané metriky.
     */
    public boolean doWeHaveEverything() {
        for (double metric : metrics) {
            if (metric < 0) {
                return false; // Niektorá metrika sa zabudla zmerať
            }
        }

        return true;
    }

}
