package mh.optimizer;

import java.util.HashMap;
import java.util.Map;

public class CorpusProcessor {

    // Pridali sme parameter maxN (napr. 5 pre 5-gramy, alebo 3 ak dlhé behy nechceme)
    public static NGramStats process(Map<String, Long> wordFreqs, int maxN, char[] layoutMatrix) {
        long totalMonograms = 0;
        NGramStats stats = new NGramStats(0);

        // Rýchly lookup set pre klávesy, ktoré reálne máme na klávesnici
        boolean[] allowedChars = new boolean[256];
        for (char c : layoutMatrix) {
            if (c < 256) allowedChars[c] = true;
        }

        System.out.println("Spúšťam inteligentnú N-gramovú kompresiu korpusu (Fixná dĺžka 5 pre behy)...");

        // Dočasná mapa na agregáciu dlhých N-gramov, aby sme nemali duplicity
        Map<String, Long> aggregatedLongs = new HashMap<>();

        for (Map.Entry<String, Long> entry : wordFreqs.entrySet()) {
            String word = entry.getKey();
            long freq = entry.getValue();
            int len = word.length();

            if (len == 0) continue;

            // 🔥 KLÚČOVÁ ZMENA: Ak slovo obsahuje akýkoľvek neprítomný znak, letí celé preč!
            boolean valid = true;
            for (int i = 0; i < len; i++) {
                char c = word.charAt(i);
                if (c >= 256 || !allowedChars[c]) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue; // Zosynchronizované s Analyzerom
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

            // 🔥 4. DYNAMICKÁ NADSTAVBA: Hľadáme striktne a LEN 5-gramy (dĺžku 4 sme vyhodili)
            if (maxN >= 5) {
                int n = 5; // Fixná dĺžka jednoručných behov
                for (int i = 0; i <= len - n; i++) {
                    String sub = word.substring(i, i + n);
                    aggregatedLongs.put(sub, aggregatedLongs.getOrDefault(sub, 0L) + freq);
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

        // 🔥 5. PREVOD DO SUPER-RÝCHLYCH PLOCHÝCH POLÍ (IBA PRE DĹŽKU 5)
        int numLongs = aggregatedLongs.size();
        finalStats.longNGramsCount = numLongs;
        finalStats.longNGramsFreqs = new long[numLongs];

        // 💥 ZMENA: Staré polia longNGramsLengths a longNGramsOffsets sú kompletne zmazané

        // Keďže vieme, že každý jeden n-gram má presne 5 znakov, alokujeme natvrdo (numLongs * 5)
        finalStats.longNGramsConcat = new byte[numLongs * 5];

        int idx = 0;
        for (Map.Entry<String, Long> e : aggregatedLongs.entrySet()) {
            String seqStr = e.getKey(); // Má garantovanú dĺžku 5 znakov

            finalStats.longNGramsFreqs[idx] = e.getValue();

            // 💥 ZMENA: Ofset rátame priamo z indexu ako čistú násobilku piatich (idx * 5)
            int currentOffset = idx * 5;

            // Nasypeme 5 bajtov lineárne za sebou bez pointer-chasingu
            finalStats.longNGramsConcat[currentOffset]     = (byte) seqStr.charAt(0);
            finalStats.longNGramsConcat[currentOffset + 1] = (byte) seqStr.charAt(1);
            finalStats.longNGramsConcat[currentOffset + 2] = (byte) seqStr.charAt(2);
            finalStats.longNGramsConcat[currentOffset + 3] = (byte) seqStr.charAt(3);
            finalStats.longNGramsConcat[currentOffset + 4] = (byte) seqStr.charAt(4);

            idx++;
        }

        System.out.println("Kompresia úspešná. Unikátnych 5-gramov po zlúčení: " + finalStats.longNGramsCount);
        return finalStats;
    }

}
