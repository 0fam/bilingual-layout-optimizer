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

        // 🚀 OPRAVA: Zosypeme filtre z n-gramov AJ z prstov do jedného globálneho skóre
        double product = Arrays.stream(metricCoefficients).filter(value -> value >= 0).reduce(1.0, (a, b) -> a * b);
        long count = Arrays.stream(metricCoefficients).filter(value -> value >= 0).count();

        double fingerProduct = Arrays.stream(fingerCoefficients).filter(value -> value >= 0).reduce(1.0, (a, b) -> a * b);
        long fingerCount = Arrays.stream(fingerCoefficients).filter(value -> value >= 0).count();

        product *= fingerProduct;
        count += fingerCount;

        if (count == 0) return 0.0;

        return totalScore = Math.pow(product, 1.0 / count) * 100.0; // toto je priemer n-rozmerneho telesa, namiesto objemu
//        return globalScore = poweredProduct * 10000; // ak preferujes hrube skore, pozor, vysledky budu logaritmickeho charakteru

    }

    /**
     * KĽÚČOVÉ DOPLNENIE: Preráta surové sumy na reálne percentá s ochranou proti deleniu nulou.
     */
    private void computePercents() {
        // =========================================================================
        // 🚀 1. DEKONŠTRUKCIA 30 POZÍCIÍ NA PRSTY
        // =========================================================================
        for (int i = 0; i < keyFrequencies.length; i++) {
            double rawValue = keyFrequencies[i];

            // Ak sa pozícia v texte vôbec nevyskytla, skočí tam čistá nula
            if (rawValue < 0) rawValue = 0.0;

            // Vypočítame reálne percento stlačenia tohto jedného konkrétneho klávesu
            keyPercents[i] = (nGramStats.getTotalMonogramsCount() == 0) ? 0.0 : (rawValue * 100.0) / nGramStats.getTotalMonogramsCount();

            // 🎯 MAGICKÝ TRIK: Z tvojej topológie zistíme ID prsta (0 až 7) pre túto pozíciu
            Finger f = KeyboardTopology.POS_TO_FINGER[i];

            // Pripočítame percento klávesu k celkovému zaťaženiu prsta
            fingerPercents[f.ordinal()] += keyPercents[i];
        }

        // =========================================================================
        // 🧮 2. PREPOČET OSTATNÝCH N-GRAMOVÝCH METRÍK (SFB, SFS, ALT...)
        // =========================================================================
        for (int i = 0; i < METRIC_COUNT; i++) {
            MetricType type = MetricType.values()[i];

            double rawValue = metrics[i];

            // Ak optimizér túto metriku vôbec nezmeral, necháme predvolenú hodnotu
            if (rawValue < 0) {
                continue;
            }

            // Nový, moderný switch expression priradí správneho menovateľa pre všetkých 20 metrík
            long denominator = switch (type) {
                case HOME_ROW, STRETCH, RUNS -> nGramStats.getTotalMonogramsCount();
                case SFB, ALTERNATION, SCISSORS, LSB, LHB -> nGramStats.getTotalBigramsCount();
                case SFS -> nGramStats.getTotalSkipgramsCount();
                case REDIRECTS, ROLL_IN, ROLL_OUT -> nGramStats.getTotalTrigramsCount();
                default -> {
                    System.out.println("Error: metrika nema zadefinovany prepoct na percenta. See LayoutStats.");
                    yield nGramStats.getTotalMonogramsCount();
                }
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
        // 🚀 NOVÉ: Vyriešime 8 prstov pod hlavičkou FINGER_LOAD
        for (Finger f: Finger.values()) {
            fingerCoefficients[f.ordinal()] = weights.calculateFingerCoefficient(f, fingerPercents[f.ordinal()]);
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
