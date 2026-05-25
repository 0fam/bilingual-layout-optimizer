package mh.optimizer;

import java.util.Map;

public class CorpusProcessor {

    public static NGramStats process(Map<String, Long> wordFreqs) {
        long totalMonograms = 0;

        // Vytvoríme prázdny kontajner
        NGramStats stats = new NGramStats(0); // Zatiaľ 0, upravíme neskôr

        System.out.println("Spúšťam N-gramovú kompresiu korpusu...");

        for (Map.Entry<String, Long> entry : wordFreqs.entrySet()) {
            String word = entry.getKey();
            long freq = entry.getValue();
            int len = word.length();

            if (len == 0) continue;

            // Zabezpečíme, že znaky nepadnú mimo poľa 256
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
                stats.bigrams[c1][c2] += freq;

                // Skipgram (jedno písmeno medzitým, dôležité pre SFS)
                if (i < len - 2) {
                    char c3 = word.charAt(i + 2);
                    stats.skipgrams[c1][c3] += freq;
                }
            }

            // 3. TRIGRAMY (Pre Rolls a Redirects)
            for (int i = 0; i < len - 2; i++) {
                char c1 = word.charAt(i);
                char c2 = word.charAt(i + 1);
                char c3 = word.charAt(i + 2);
                stats.trigrams[c1][c2][c3] += freq;
            }

            // TENTO BLOK PRIDÁME DO VNÚTRA CYKLU PRE SLOVÁ V CorpusProcessor:
            // Povedzme, že chceme sledovať všetko od 4-gramov po 6-gramy
            int minN = 4;
            int maxN = 6;

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

        System.out.println("Kompresia úspešná. Celkovo monogramov: " + totalMonograms);

        // Malý trik: v Jave nevieme zmeniť final premennú po inicializácii jednoducho,
        // tak si vytvoríme nový finálny objekt s presným počtom.
        NGramStats finalStats = new NGramStats(totalMonograms);
        System.arraycopy(stats.monograms, 0, finalStats.monograms, 0, 256);
        for(int i = 0; i < 256; i++) {
            System.arraycopy(stats.bigrams[i], 0, finalStats.bigrams[i], 0, 256);
            System.arraycopy(stats.skipgrams[i], 0, finalStats.skipgrams[i], 0, 256);
            for(int j = 0; j < 256; j++) {
                System.arraycopy(stats.trigrams[i][j], 0, finalStats.trigrams[i][j], 0, 256);
            }
        }

        return finalStats;
    }
}
