package mh.layout.core.stats;

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

    // --- MONOGRAMY (Tvoja chýbajúca úroveň) ---
    public void addMonogram(char c, double freq) {
        monograms[c] = freq;
    }

    public double getMonogramFreq(char c) {
        return monograms[c];
    }

    // --- BIGRAMY ---
    public void addBigram(char c1, char c2, double freq) {
        bigrams[(c1 << 8) | c2] = freq;
    }

    public double getBigramFreq(char c1, char c2) {
        return bigrams[(c1 << 8) | c2];
    }

    // --- SKIPGRAMY (SFS - Sendviče) ---
    public void addSkipgram(char c1, char c2, double freq) {
        skipgrams[(c1 << 8) | c2] = freq;
    }

    public double getSkipgramFreq(char c1, char c2) {
        return skipgrams[(c1 << 8) | c2];
    }

    // --- TRIGRAMY (Redirects / Pinbally) ---
    public void addTrigram(char c1, char c2, char c3, double freq) {
        trigrams[(c1 << 16) | (c2 << 8) | c3] = freq;
    }

    public double getTrigramFreq(char c1, char c2, char c3) {
        return trigrams[(c1 << 16) | (c2 << 8) | c3];
    }

    // --- 5-GRAMY PRE JEDNORUČNÉ BEHY (Runs) ---
    /**
     * Zabalí 5 znakov do jedného 64-bitového longu a uloží frekvenciu.
     */
    public void addRun5Gram(char c1, char c2, char c3, char c4, char c5, double freq) {
        long key = ((long) c1 << 32) | ((long) c2 << 24) | (c3 << 16) | (c4 << 8) | c5;
        runs5Grams.put(key, freq);
    }

    /**
     * Bleskové vytiahnutie frekvencie 5-gramu pomocou zabaleného long kľúča.
     */
    public double getRun5GramFreq(char c1, char c2, char c3, char c4, char c5) {
        long key = ((long) c1 << 32) | ((long) c2 << 24) | (c3 << 16) | (c4 << 8) | c5;
        return runs5Grams.getOrDefault(key, 0.0);
    }

    /**
     * Umožní analyzátoru alebo prekladaču prejsť všetky zaznamenané 5-gramy pre detailný report.
     */
    public Map<Long, Double> getAllRun5Grams() {
        return runs5Grams;
    }
}
