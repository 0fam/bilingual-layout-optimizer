package mh.layout.core.io;

import mh.layout.core.stats.NGramStats;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class CorpusProcessor {

    private CorpusProcessor() {}

    /**
     * Načíta korpus, odmasťuje diakritiku, plní ploché N-gramy pre Optimizér
     * a vracia čistý slovník "písacích kusov" rozdelených medzerami pre Analyzátor.
     * * @param filePath Cesta k súboru korpusu
     * @param stats Pamäťová schránka (štruktúra), ktorú ideme naplniť
     * @param weight Násobiteľ frekvencie (ak chceš ručne vyvážiť menší korpus)
     */
    public static Map<String, Double> processFile(String filePath, NGramStats stats, double weight) throws IOException {
        Map<String, Double> wordDictionary = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                // --- 1. ODMASTENIE DIAKRITIKY A UPPERCASE ---
                String cleanedLine = normalizeLine(line);

                // --- 2. ZBER KUSOV TEXTU (Chunks) PRE ANALYZÁTOR ---
                // Sekáme výhradne podľa medzier. Slová ako Qaa'ed alebo text s otáznikom ostanú v kope.
                String[] chunks = cleanedLine.split("\\s+");
                for (String chunk : chunks) {
                    if (chunk.length() >= 4) { // Krátke kusy pre štatistiku behov preskakujeme
                        wordDictionary.put(chunk, wordDictionary.getOrDefault(chunk, 0.0) + weight);
                    }
                }

                // --- 3. KĹZAVÉ OKNO PRE N-GRAMY (Hot-path plnenie) ---
                int idx1 = -1; // Znak pred-predchodca (c1)
                int idx2 = -1; // Znak predchodca (c2)

                for (int i = 0; i < cleanedLine.length(); i++) {
                    char c3 = cleanedLine.charAt(i);

                    // Filtrujeme iba znaky, ktoré reálne sedia na našej 30-klávesovej topológii
                    // Povolíme: A-Z, medzeru a presne 4 tvoje symboly (, . ? -)
                    boolean isValid = (c3 >= 'A' && c3 <= 'Z') || c3 == ' ' || c3 == ',' || c3 == '.' || c3 == '?' || c3 == '-';

                    if (!isValid) {
                        // Ak je to akýkoľvek iný znak (číslo, úvodzovka, enter), zlomí to plynulý rytmus a resetuje okno
                        idx1 = -1;
                        idx2 = -1;
                        continue;
                    }

                    // A. Zápis monogramu
                    stats.addMonogram(c3, stats.getMonogramFreq(c3) + weight);

                    // B. Zápis bigramu (Ak máme v pamäti aspoň 2 znaky po sebe)
                    if (idx2 != -1) {
                        char c2 = (char) idx2;
                        stats.addBigram(c2, c3, stats.getBigramFreq(c2, c3) + weight);
                    }

                    // C. Zápis trigramu a skipgramu (Ak máme v pamäti plné okno 3 znakov)
                    if (idx1 != -1 && idx2 != -1) {
                        char c1 = (char) idx1;
                        char c2 = (char) idx2;

                        // Trigram pre zmeny smeru (c1 -> c2 -> c3)
                        stats.addTrigram(c1, c2, c3, stats.getTrigramFreq(c1, c2, c3) + weight);

                        // Skipgram (SFS - Sendvič): Prvý a tretí znak, stredný sa ignoruje
                        stats.addSkipgram(c1, c3, stats.getSkipgramFreq(c1, c3) + weight);
                    }

                    // Posun kĺzavého okna o jeden krok vpred
                    idx1 = idx2;
                    idx2 = c3;
                }
            }
        }

        return wordDictionary;
    }

    /**
     * Pomocná funkcia, ktorá kompletne strhne slovenskú diakritiku a hodí text do Uppercase.
     */
    private static String normalizeLine(String text) {
        char[] src = text.toUpperCase().toCharArray();
        for (int i = 0; i < src.length; i++) {
            switch (src[i]) {
                case 'Á': case 'Ä': src[i] = 'A'; break;
                case 'Č': src[i] = 'C'; break;
                case 'Ď': src[i] = 'D'; break;
                case 'É': src[i] = 'E'; break;
                case 'Í': src[i] = 'I'; break;
                case 'Ĺ': case 'Ľ': src[i] = 'L'; break;
                case 'Ň': src[i] = 'N'; break;
                case 'Ó': case 'Ô': src[i] = 'O'; break;
                case 'Ŕ': src[i] = 'R'; break;
                case 'Š': src[i] = 'S'; break;
                case 'Ť': src[i] = 'T'; break;
                case 'Ú': src[i] = 'U'; break;
                case 'Ý': src[i] = 'Y'; break;
                case 'Ž': src[i] = 'Z'; break;
                default: break;
            }
        }
        return new String(src);
    }
}
