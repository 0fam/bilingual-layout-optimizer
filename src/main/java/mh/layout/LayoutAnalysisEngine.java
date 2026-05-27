package mh.layout;

import mh.layout.stats.NGramStats;
import mh.layout.stats.Weights;
import java.util.Map;

public final class LayoutAnalysisEngine {
    private final Weights weights = new Weights();
    private final NGramStats statsSK = new NGramStats();
    private final NGramStats statsEN = new NGramStats();

    private Map<String, Double> dictSK;
    private Map<String, Double> dictEN;

    private final double slovakRatio = 0.60;

    public void runStandalone(String targetPath) {
        // 1. Inicializuj ergonomické váhy
        // 2. Volaj CorpusProcessor na načítanie invariantných štatistík textu (SK a EN)
        // 3. Načítaj súbor alebo prebehni zložku 'targetPath' a vytiahni z nej matice char[]
        // 4. Každú maticu prežeň cez Evaluator.evaluate(...)
        // 5. Spáruj názov súboru/layoutu s jeho kombinovaným skóre
        // 6. Zorď výsledky zostupne (od najvyššieho skóre po najnižšie)
        // 7. Vytlač do konzoly nádherný bilingválny LEADERBOARD
    }
}
