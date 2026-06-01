package mh.layout.evaluator;

import mh.layout.stats.NGramStats;

import static mh.layout.topology.KeyboardTopology.KEY_COUNT;

public record NGramsMultipliers(
    double monoX,
    double biX,
    double skipX,
    double triX
) {

    public static NGramsMultipliers compute(double totalMonograms, double totalBigrams, double totalSkipgrams, double totalTrigrams) {
        // =========================================================================
        // 🚀 HACK: Predpočítané multiplikátory pre všetky typy n-gramov
        // =========================================================================
        double mgMulti = (totalMonograms == 0) ? 0.0 : (100.0 / totalMonograms);
        double bgMulti = (totalBigrams == 0)   ? 0.0 : (100.0 / totalBigrams);
        double sgMulti = (totalSkipgrams == 0) ? 0.0 : (100.0 / totalSkipgrams);
        double tgMulti = (totalTrigrams == 0)  ? 0.0 : (100.0 / totalTrigrams);

        return new NGramsMultipliers(mgMulti, bgMulti, sgMulti, tgMulti);
    }

    public static NGramsMultipliers compute(char[] matrix, NGramStats stats) {
        // 🚀 INVARIANTNÉ MENOVATELE: Vypočítané iba raz na začiatku pre brutálny návrat rýchlosti
        double mono = 0;
        double bi = 0;
        double skip = 0;
        double tri = 0;

        // =========================================================================
        // ⚡ JEDNORAZOVÝ PREDPREČÍTANÝ SÚČET FREKVENCIÍ PRE CELÝ SET ZNAKOV
        // =========================================================================
        for (int i = 0; i < KEY_COUNT; i++) {
            mono += stats.getMonogramFreq(matrix[i]);
            for (int j = 0; j < KEY_COUNT; j++) {
                bi += stats.getBigramFreq(matrix[i], matrix[j]);
                skip   += stats.getSkipgramFreq(matrix[i], matrix[j]);

                for (int k = 0; k < KEY_COUNT; k++) {
                    tri += stats.getTrigramFreq(matrix[i], matrix[j], matrix[k]);
                }
            }
        }

        return compute(mono, bi, skip, tri);
    }

}
