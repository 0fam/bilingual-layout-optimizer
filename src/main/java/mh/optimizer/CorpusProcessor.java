package mh.optimizer;

import java.util.HashMap;
import java.util.Map;

public class CorpusProcessor {

    // Pridali sme parameter maxN (napr. 5 pre 5-gramy, alebo 3 ak dlhé behy nechceme)
    public static NGramStats process(Map<String, Long> wordFreqs, int maxN) {
        long totalMonograms = 0;
        NGramStats stats = new NGramStats(0);

        System.out.println("Spúšťam inteligentnú N-gramovú kompresiu korpusu (Max N = " + maxN + ")...");

        // Dočasná mapa na agregáciu dlhých N-gramov, aby sme nemali duplicity
        Map<String, Long> aggregatedLongs = new HashMap<>();

        for (Map.Entry<String, Long> entry : wordFreqs.entrySet()) {
            String word = entry.getKey();
            long freq = entry.getValue();
            int len = word.length();

            if (len == 0) continue;

            boolean valid = true;
            for (int i = 0; i < len; i++) {
                if (word.charAt(i) >= 256) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            // 1. MONOGRAMY
            for (int i = 0; i < len; i++) {
                char c = word.charAt(i);
                stats.monograms[c] += freq;
                totalMonograms += freq;
            }

            // 2. BIGRAMY a SKIPGRAMY
            for (int i = 0; i < len - 1; i++) {
                char c1 = word.charAt(i);
                char c2 = word.charAt(i + 1);

                // 🔥 ZMENA: Zápis do plochého 1D poľa pre bigramy
                stats.bigrams[(c1 << 8) | c2] += freq;

                if (i < len - 2) {
                    char c3 = word.charAt(i + 2);

                    // 🔥 ZMENA: Zápis do plochého 1D poľa pre skipgramy
                    stats.skipgrams[(c1 << 8) | c3] += freq;
                }
            }

            // 3. TRIGRAMY
            for (int i = 0; i < len - 2; i++) {
                char c1 = word.charAt(i);
                char c2 = word.charAt(i + 1);
                char c3 = word.charAt(i + 2);

                // 🔥 ZMENA: Zápis do plochého 1D poľa pre trigramy cez bitový posun
                stats.trigrams[(c1 << 16) | (c2 << 8) | c3] += freq;
            }

            // 4. DYNAMICKÁ NADSTAVBA (Zlučujeme rovnaké 4 a 5-gramy)
            if (maxN >= 4) {
                for (int n = 4; n <= maxN; n++) {
                    for (int i = 0; i <= len - n; i++) {
                        String sub = word.substring(i, i + n);
                        aggregatedLongs.put(sub, aggregatedLongs.getOrDefault(sub, 0L) + freq);
                    }
                }
            }
        }

        // Preklopenie agregovaných dát do finálneho objektu
        NGramStats finalStats = new NGramStats(totalMonograms);
        System.arraycopy(stats.monograms, 0, finalStats.monograms, 0, 256);

        // 🔥 ZMENA: Vyhodili sme zložité vnorené cykly. Keďže sú polia ploché,
        // operačný systém ich skopíruje celé naraz v jedinom bleskovom pamäťovom bloku.
        System.arraycopy(stats.bigrams, 0, finalStats.bigrams, 0, 256 * 256);
        System.arraycopy(stats.skipgrams, 0, finalStats.skipgrams, 0, 256 * 256);
        System.arraycopy(stats.trigrams, 0, finalStats.trigrams, 0, 256 * 256 * 256);

        // 🔥 ZMENA: Prevod z dočasnej mapy do super-rýchlych plochých 1D polí
        int numLongs = aggregatedLongs.size();
        finalStats.longNGramsCount = numLongs;
        finalStats.longNGramsFreqs = new long[numLongs];
        finalStats.longNGramsLengths = new int[numLongs];
        finalStats.longNGramsOffsets = new int[numLongs];

        // Zistíme presnú celkovú dĺžku v bajtoch pre jedno ploché pole
        int totalBytes = 0;
        for (String seqStr : aggregatedLongs.keySet()) {
            totalBytes += seqStr.length();
        }
        finalStats.longNGramsConcat = new byte[totalBytes];

        int idx = 0;
        int currentOffset = 0;
        for (Map.Entry<String, Long> e : aggregatedLongs.entrySet()) {
            String seqStr = e.getKey();
            int len = seqStr.length();

            finalStats.longNGramsFreqs[idx] = e.getValue();
            finalStats.longNGramsLengths[idx] = len;
            finalStats.longNGramsOffsets[idx] = currentOffset;

            // Nasypeme znaky za sebou do jedného flat poľa
            for (int i = 0; i < len; i++) {
                finalStats.longNGramsConcat[currentOffset + i] = (byte) seqStr.charAt(i);
            }
            currentOffset += len;
            idx++;
        }

        System.out.println("Kompresia úspešná. Unikátnych dlhých N-gramov po zlúčení: " + finalStats.longNGramsCount);
        return finalStats;
    }

}
