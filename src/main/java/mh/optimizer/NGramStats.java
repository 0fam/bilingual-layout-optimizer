package mh.optimizer;

import java.util.ArrayList;
import java.util.List;

public class NGramStats {
    public final long totalMonograms;
    public final long[] monograms = new long[256];
    // 🔥 ZMENA: Polia sploštené do 1D pre bleskový prístup v L3 cache
    public final long[] bigrams = new long[256 * 256];
    public final long[] skipgrams = new long[256 * 256];
    // 256 * 256 * 256 = 16 777 216 prvkov (cca 134 MB v pamäti, ideálne pre L3 cache)
    public final long[] trigrams = new long[256 * 256 * 256];

    // --- NOVINKA: Univerzálny zoznam pre dlhé reálne N-gramy (Sparsity) ---
    // 🔥 ZMENA: Objektový ArrayList nahradený plochými primitívnymi poliami pre bleskový prelet v cache
    public int longNGramsCount = 0;
    public long[] longNGramsFreqs;
    public byte[] longNGramsConcat;

    public NGramStats(long totalMonograms) {
        this.totalMonograms = totalMonograms;
    }
}
