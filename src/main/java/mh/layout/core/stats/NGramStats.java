package mh.layout.core.stats;

public final class NGramStats {

    // Veľkosť poľa pre kombináciu dvoch ASCII znakov (256 * 256 = 65536 pozícií)
    private static final int BIGRAM_ARRAY_SIZE = 256 * 256;

    // Veľkosť poľa pre kombináciu troch ASCII znakov (256 * 256 * 256 = 16 777 216 pozícií)
    // 16 MB pole sa bez problémov zmestí do modernej L3 cache procesora
    private static final int TRIGRAM_ARRAY_SIZE = 256 * 256 * 256;

    // Ploché polia pre bleskový O(1) prístup
    private final double[] bigrams = new double[BIGRAM_ARRAY_SIZE];
    private final double[] skipgrams = new double[BIGRAM_ARRAY_SIZE]; // Pre SFS (sendviče)
    private final double[] trigrams = new double[TRIGRAM_ARRAY_SIZE]; // Pre Redirects (pinbally)

    // Na počítanie behov (runs) nám stačí pole pre slová/kúsky s dĺžkou 5
    // Znaky bleskovo zakódujeme do jedného longu alebo použijeme indexáciu podľa potreby optimizéra
    private final double[] _5gramsruns = new double[BIGRAM_ARRAY_SIZE]; // Prispôsobíme podľa tvojej logiky behov

    /**
     * Zápis frekvencie bigramu do plochého poľa pomocou bitového posunu
     */
    public void addBigram(char c1, char c2, double freq) {
        int index = (c1 << 8) | c2;
        bigrams[index] = freq;
    }

    public double getBigramFreq(char c1, char c2) {
        return bigrams[(c1 << 8) | c2];
    }

    /**
     * Zápis frekvencie skipgramu (SFS - znak, niečo medzi tým, znak)
     */
    public void addSkipgram(char c1, char c2, double freq) {
        int index = (c1 << 8) | c2;
        skipgrams[index] = freq;
    }

    public double getSkipgramFreq(char c1, char c2) {
        return skipgrams[(c1 << 8) | c2];
    }

    /**
     * Zápis frekvencie trigramu (pre zmeny smeru)
     */
    public void addTrigram(char c1, char c2, char c3, double freq) {
        int index = (c1 << 16) | (c2 << 8) | c3;
        trigrams[index] = freq;
    }

    public double getTrigramFreq(char c1, char c2, char c3) {
        return trigrams[(c1 << 16) | (c2 << 8) | c3];
    }
}
