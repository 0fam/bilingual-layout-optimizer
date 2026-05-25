package mh.optimizer;

import java.util.ArrayList;
import java.util.List;

public class NGramStats {
    public final long totalMonograms;
    public final long[] monograms = new long[256];
    public final long[][] bigrams = new long[256][256];
    public final long[][] skipgrams = new long[256][256];
    public final long[][][] trigrams = new long[256][256][256];

    // --- NOVINKA: Univerzálny zoznam pre dlhé reálne N-gramy (Sparsity) ---
    public final List<CompactNGram> longNGrams = new ArrayList<>();

    public NGramStats(long totalMonograms) {
        this.totalMonograms = totalMonograms;
    }

}
