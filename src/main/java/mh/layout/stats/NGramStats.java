package mh.layout.stats;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public final class NGramStats {

    private static final int MONOGRAM_ARRAY_SIZE = 256;
    private static final int BIGRAM_ARRAY_SIZE = 256 * 256;
    private static final int TRIGRAM_ARRAY_SIZE = 256 * 256 * 256;

    // Ploché polia pre bleskový O(1) prístup v hot-pathe
    private final double[] monograms = new double[MONOGRAM_ARRAY_SIZE];
    private final double[] bigrams = new double[BIGRAM_ARRAY_SIZE];
    private final double[] skipgrams = new double[BIGRAM_ARRAY_SIZE];
    private final double[] trigrams = new double[TRIGRAM_ARRAY_SIZE];

    // Pre 5-gramy nemôžeme použiť ploché pole (bolo by príliš obrovské).
    // Použijeme mapu, kde kľúčom je 5 znakov bleskovo zabalených do jedného objektu Long.
    private final Map<Long, Double> runs5Grams = new HashMap<>();

    // 📈 NOVÉ: Globálne počítadlá pre celý statický text (korpus)
    // 🔍 NOVÉ: Čisté, bleskové gettery pre Evaluator a LayoutStats
    @Getter
    private long totalMonograms = 0;
    @Getter
    private long totalBigrams = 0;
    @Getter
    private long totalSkipgrams = 0;
    @Getter
    private long totalTrigrams = 0;

    // --- MONOGRAMY ---
    public void incrementMonogram(char c) {
        monograms[c]++;
        totalMonograms++; // <- Tu
    }

    public double getMonogramFreq(char c) {
        return monograms[c];
    }

    // --- BIGRAMY ---
    public void incrementBigram(char c1, char c2) {
        bigrams[(c1 << 8) | c2]++;
        totalBigrams++;   // <- Tu
    }

    public double getBigramFreq(char c1, char c2) {
        return bigrams[(c1 << 8) | c2];
    }

    // --- SKIPGRAMY (SFS - Sendviče) ---
    public void incrementSkipgram(char c1, char c2) {
        skipgrams[(c1 << 8) | c2]++;
        totalSkipgrams++; // <- Tu
    }

    public double getSkipgramFreq(char c1, char c2) {
        return skipgrams[(c1 << 8) | c2];
    }

    // --- TRIGRAMY (Redirects / Pinbally) ---
    public void incrementTrigram(char c1, char c2, char c3) {
        trigrams[(c1 << 16) | (c2 << 8) | c3]++;
        totalTrigrams++;  // <- Tu
    }

    public double getTrigramFreq(char c1, char c2, char c3) {
        return trigrams[(c1 << 16) | (c2 << 8) | c3];
    }

}
