package mh.layout.stats;

import lombok.Setter;

import java.util.Arrays;

import static mh.layout.stats.MetricType.ALTERNATION;
import static mh.layout.stats.MetricType.SFB;

public final class LayoutStats {
    private static final int METRIC_COUNT = MetricType.values().length;

    private final Weights weights;

    // 👑 CELKOVÝ VÝSLEDOK
    private double totalScore = -1;

    @Setter
    long totalMonograms = 0;
    @Setter
    long totalBigrams = 0;
    @Setter
    long totalSkipgrams = 0;
    @Setter
    long totalTrigrams = 0;

    // 📊 VŠETKY METRIKY V JEDNOM PLOCHOM POĽA (Veľkosť 20 podľa MetricType)
    private final double[] metrics = new double[METRIC_COUNT];
    private final double[] metricPercents = new double[METRIC_COUNT];
    private final double[] metricCoefficients = new double[METRIC_COUNT];


    public LayoutStats(Weights weights) {
        this.weights = weights;
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

        return totalScore = Math.pow(product, 1.0 / count) * 100.0; // toto je priemer n-rozmerneho telesa, namiesto objemu
//        return globalScore = poweredProduct * 10000; // ak preferujes hrube skore, pozor, vysledky budu logaritmickeho charakteru

    }

    private void computePercents() {
        for (int i = 0; i < METRIC_COUNT; i++) {
            switch (i) {
                case SFB, ALTERNATION -> metricPercents[i] = metrics[i]/totalBigrams;
                case SFS -> {}
                case SCISSORS -> {}
                case REDIRECTS -> {}
                case LSB -> {}
                case LHB -> {}
                case STRETCH -> {}
            }
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
