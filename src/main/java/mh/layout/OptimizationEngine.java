package mh.layout;

import mh.layout.stats.NGramStats;
import mh.layout.stats.Weights;
import java.util.Map;

public final class OptimizationEngine {
    private final Weights weights = new Weights();
    private final NGramStats statsSK = new NGramStats();
    private final NGramStats statsEN = new NGramStats();

    private Map<String, Double> dictSK;
    private Map<String, Double> dictEN;

    private final double slovakRatio = 0.60;
    private final long optimizationTimeMs = 10000;

    public void run() {
        // 1. Inicializuj váhy
        // 2. Volaj CorpusProcessor na načítanie dát
        // 3. Spusť ParallelOptimizer
        // 4. Uspi vlákno na stanovený čas (optimizationTimeMs)
        // 5. Zastav optimizér a vezmi víťaznú maticu
        // 6. Odovzdaj výsledok do LayoutAnalyzer a ulož na disk
    }

    private void configureWeights() {}
}
