package mh.layout.stats;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public void printTopMonograms(String label) {
        System.out.println("\n📊 --- KOMPLETNÝ SUROVÝ PREHĽAD KORPUSU: " + label.toUpperCase() + " ---");
        System.out.println("-------------------------------------------------------------------------");

        if (this.totalMonograms == 0) {
            System.out.println("   Korpus neobsahuje žiadne dáta.");
            return;
        }

        // Lokálny záznam pre bleskové spracovanie
        record CharFreq(char ch, double count) {}
        List<CharFreq> frequencyList = new ArrayList<>();

        // 🚀 ZMENA: Už žiadna fixná abeceda. Skenujeme celé ploché pole (všetkých 256 ASCII pozícií)
        for (int i = 0; i < this.monograms.length; i++) {
            double count = this.monograms[i];
            if (count > 0) {
                frequencyList.add(new CharFreq((char) i, count));
            }
        }

        // Zoradenie zostupne od najpoužívanejšieho znaku v celom súbore
        frequencyList.sort((a, b) -> Double.compare(b.count(), a.count()));

        int printCount = 0;
        int rank = 1;

        for (CharFreq cf : frequencyList) {
            double percentage = (cf.count() / this.totalMonograms) * 100.0;

            // 👁️ UX FIX: Ošetrenie neviditeľných a špeciálnych znakov pre tabuľku
            String charDisplay;
            if (cf.ch() == ' ') {
                charDisplay = "[SPC]"; // Medzera
            } else if (cf.ch() == '\t') {
                charDisplay = "[TAB]";
            } else if (cf.ch() < 32) {
                charDisplay = String.format("CTL_%02d", (int) cf.ch()); // Kontrolné znaky (CR, LF atď.)
            } else {
                charDisplay = String.format(" '%c' ", cf.ch()); // Klasický viditeľný znak
            }

            // Výpis do dvoch stĺpcov inšpirovaný tvojím starým kódom
            System.out.printf("  %2d. %-7s : %5.2f%% (%12d x)", rank++, charDisplay, percentage, (long) cf.count());

            printCount++;
            if (printCount % 2 == 0) {
                System.out.println();
            } else {
                System.out.print("   |   ");
            }
        }

        if (printCount % 2 != 0) {
            System.out.println();
        }
        System.out.println("-------------------------------------------------------------------------\n");
    }

}
