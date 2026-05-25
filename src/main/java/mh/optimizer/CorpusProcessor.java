package mh.optimizer;

import java.util.Map;

public class CorpusProcessor {

    // Pridali sme parameter maxN (napr. 5 pre 5-gramy, alebo 3 ak dlhé behy nechceme)
    public static NGramStats process(Map<String, Long> wordFreqs, int maxN) {
        long totalMonograms = 0;
        NGramStats stats = new NGramStats(0);

        System.out.println("Spúšťam N-gramovú kompresiu korpusu (Max N = " + maxN + ")...");

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

            // 1. FIXED MONOGRAMY
            for (int i = 0; i < len; i++) {
                char c = word.charAt(i);
                stats.monograms[c] += freq;
                totalMonograms += freq;
            }

            // 2. FIXED BIGRAMY a SKIPGRAMY
            for (int i = 0; i < len - 1; i++) {
                char c1 = word.charAt(i);
                char c2 = word.charAt(i + 1);
                stats.bigrams[c1][c2] += freq;

                if (i < len - 2) {
                    char c3 = word.charAt(i + 2);
                    stats.skipgrams[c1][c3] += freq;
                }
            }

            // 3. FIXED TRIGRAMY
            for (int i = 0; i < len - 2; i++) {
                char c1 = word.charAt(i);
                char c2 = word.charAt(i + 1);
                char c3 = word.charAt(i + 2);
                stats.trigrams[c1][c2][c3] += freq;
            }

            // 4. DYNAMICKÁ NADSTAVBA (Spustí sa, len ak požaduješ N >= 4)
            if (maxN >= 4) {
                int minN = 4;
                for (int n = minN; n <= maxN; n++) {
                    for (int i = 0; i <= len - n; i++) {
                        byte[] seq = new byte[n];
                        boolean validSeq = true;

                        for (int j = 0; j < n; j++) {
                            char ch = word.charAt(i + j);
                            if (ch >= 256) { validSeq = false; break; }
                            seq[j] = (byte) ch;
                        }

                        if (validSeq) {
                            stats.longNGrams.add(new CompactNGram(seq, freq));
                        }
                    }
                }
            }
        }

        System.out.println("Kompresia úspešná. Celkovo monogramov: " + totalMonograms);
        System.out.println("Počet vyextrahovaných dlhých N-gramov: " + stats.longNGrams.size());

        // Inicializácia finálneho objektu
        NGramStats finalStats = new NGramStats(totalMonograms);

        // Kopírovanie hustých matíc (1, 2, 3-gramy)
        System.arraycopy(stats.monograms, 0, finalStats.monograms, 0, 256);
        for(int i = 0; i < 256; i++) {
            System.arraycopy(stats.bigrams[i], 0, finalStats.bigrams[i], 0, 256);
            System.arraycopy(stats.skipgrams[i], 0, finalStats.skipgrams[i], 0, 256);
            for(int j = 0; j < 256; j++) {
                System.arraycopy(stats.trigrams[i][j], 0, finalStats.trigrams[i][j], 0, 256);
            }
        }

        // 🔥 OPRAVA TVOJHO BUGU: Preklápame zoznam dlhých N-gramov do finálneho objektu
        finalStats.longNGrams.addAll(stats.longNGrams);

        return finalStats;
    }
}
