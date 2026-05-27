package mh.layout.stats;

import mh.layout.evaluator.Weights;
import mh.layout.topology.Finger;
import mh.layout.topology.KeyboardTopology;

import java.util.Arrays;

public final class LayoutStats {
    private static final int METRIC_COUNT = MetricType.values().length;
    private static final MetricType[] METRICS_CACHE = MetricType.values();
    private static final Finger[] FINGERS_CACHE = Finger.values();

    private final Weights weights;
    private final NGramStats nGramStats;

    // 👑 CELKOVÝ VÝSLEDOK
    private double totalScore = -1;

    // 📊 VŠETKY METRIKY V JEDNOM PLOCHOM POĽA (Veľkosť 20 podľa MetricType)
    private final double[] metrics = new double[METRIC_COUNT];
    private final double[] metricPercents = new double[METRIC_COUNT];
    private final double[] metricCoefficients = new double[METRIC_COUNT];
    private final double[] fingerPercents = new double[Finger.values().length];
    private final double[] fingerCoefficients = new double[Finger.values().length];

    // 📊 SUROVÉ FREKVENCIE A PERCENTÁ PRE KAŽDÝ Z 30 KLÁVESOV TOTEMU
    private final double[] keyFrequencies = new double[KeyboardTopology.KEY_COUNT];
    private final double[] keyPercents = new double[KeyboardTopology.KEY_COUNT];

    public LayoutStats(Weights weights, NGramStats nGramStats) {
        this.weights = weights;
        this.nGramStats = nGramStats;
        // Inicializujeme celé pole na -1, presne ako v tvojom pôvodnom kóde
        Arrays.fill(metrics, -1.0);
        Arrays.fill(metricPercents, -1.0);
        Arrays.fill(metricCoefficients, -1.0);
        Arrays.fill(fingerPercents, 0);
        Arrays.fill(fingerCoefficients, -1.0);
        Arrays.fill(keyFrequencies, 0);
        Arrays.fill(keyPercents, 0);
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

        double totalProduct = 1.0;
        long totalCount = 0;

        // 🚀 ULTRA-OPTIMALIZÁCIA: Jeden primitívny prechod cez metriky
        for (int i = 0; i < METRIC_COUNT; i++) {
            double coef = metricCoefficients[i];
            if (coef >= 0) {
                totalProduct *= coef;
                totalCount++;
            }
        }

        // 🚀 Jeden primitívny prechod cez prsty
        for (int i = 0; i < FINGERS_CACHE.length; i++) {
            double coef = fingerCoefficients[i];
            if (coef >= 0) {
                totalProduct *= coef;
                totalCount++;
            }
        }

        if (totalCount == 0) return 0.0;

        // Jedno jediné zavolanie Math.pow na konci je pre procesor úplná hračka
        return totalScore = Math.pow(totalProduct, 1.0 / totalCount) * 100.0; // toto je priemer n-rozmerneho telesa, namiesto objemu
//        return totalScore = totalProduct * 10000; // ak preferujes hrube skore, pozor, vysledky budu logaritmickeho charakteru
    }

    private void computePercents() {
        // 🚀 POISTKA: Vyčistíme stopu prstov pred novým sčítaním pozícií
//        Arrays.fill(fingerPercents, 0.0);

        long totalMonograms = nGramStats.getTotalMonogramsCount();

        // 🚀 1. DEKONŠTRUKCIA 30 POZÍCIÍ NA PRSTY
        for (int i = 0; i < keyFrequencies.length; i++) {
            double rawValue = keyFrequencies[i];

            // Ak sa pozícia v texte vôbec nevyskytla, skočí tam čistá nula
            if (rawValue < 0) rawValue = 0.0;

            // Vypočítame reálne percento stlačenia tohto jedného konkrétneho klávesu
            keyPercents[i] = (totalMonograms == 0) ? 0.0 : (rawValue * 100.0) / totalMonograms;

            Finger f = KeyboardTopology.POS_TO_FINGER[i];
            // Pripočítame percento klávesu k celkovému zaťaženiu prsta
            fingerPercents[f.ordinal()] += keyPercents[i];
        }

        // 🧮 2. PREPOČET OSTATNÝCH METRÍK (Používame superrýchlu METRICS_CACHE)
        for (int i = 0; i < METRIC_COUNT; i++) {
            MetricType type = METRICS_CACHE[i];

            double rawValue = metrics[i];
            if (rawValue < 0) {
                continue;
            }

            long denominator = switch (type) {
                case HOME_ROW, STRETCH, RUNS -> nGramStats.getTotalMonogramsCount();
                case SFB, ALTERNATION, SCISSORS, LSB, LHB -> nGramStats.getTotalBigramsCount();
                case SFS -> nGramStats.getTotalSkipgramsCount();
                case REDIRECTS, ROLL_IN, ROLL_OUT -> nGramStats.getTotalTrigramsCount();
            };

            // Výpočet percent s poistkou proti deleniu nulou
            metricPercents[i] = (denominator == 0) ? 0.0 : (rawValue * 100.0) / denominator;
        }
    }

    private void computeCoefficients() {
        // Používame METRICS_CACHE, žiadne klonovanie polí za behu
        for (int i = 0; i < METRIC_COUNT; i++) {
            MetricType type = METRICS_CACHE[i];

            metricCoefficients[i] = weights.calculateCoefficient(type, metricPercents[i]);
        }

        // Používame FINGERS_CACHE
        for (int i = 0; i < FINGERS_CACHE.length; i++) {
            Finger f = FINGERS_CACHE[i];
            fingerCoefficients[i] = weights.calculateFingerCoefficient(f, fingerPercents[i]);
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
