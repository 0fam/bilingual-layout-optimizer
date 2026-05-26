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
     * na základe povolených znakov z tvojho layoutu.
     * * @param filePath Cesta k súboru korpusu
     * @param stats    Pamäťová schránka, ktorú ideme naplniť
     * @param layout   Aktuálny layout klávesnice, z ktorého si vyčítame povolené symboly
     */
    public static Map<String, Double> processFile(String filePath, NGramStats stats, char[] layout) throws IOException {
        Map<String, Double> wordDictionary = new HashMap<>();

        // --- 1. DYNAMICKÁ MAPA POVOLENÝCH ZNAKOV Z LAYOUTU ---
        // Vytvoríme si bleskové lookup pole pre všetky ASCII znaky
        boolean[] allowedOnKeyboard = new boolean[256];

        // Medzerník je na klávesnici vždy povolený, hoci nie je v mriežke 3x10
        allowedOnKeyboard[' '] = true;

        // Prejdeme tvoj layout a zapíšeme si, ktoré znaky a symboly reálne vlastníš
        for (char key : layout) {
            if (key < 256) {
                // Konvertujeme pre istotu na Upper, keďže aj text normalizujeme na Upper
                allowedOnKeyboard[Character.toUpperCase(key)] = true;
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                // Odmastenie diakritiky a prevod na veľké písmená
                String cleanedLine = normalizeLine(line);

                // Zber kusov textu pre Analyzátor (sekanie podľa medzier)
                String[] chunks = cleanedLine.split("\\s+");
                for (String chunk : chunks) {
                    if (chunk.length() >= 4) { // Krátke kusy pre štatistiku behov preskakujeme
                        wordDictionary.put(chunk, wordDictionary.getOrDefault(chunk, 0.0) + 1.0);
                    }
                }

                // --- 3. KĹZAVÉ OKNO PRE N-GRAMY (Hot-path plnenie) ---
                int idx1 = -1; // Znak pred-predchodca (c1)
                int idx2 = -1; // Znak predchodca (c2)

                for (int i = 0; i < cleanedLine.length(); i++) {
                    char c3 = cleanedLine.charAt(i);

                    // Zápis monogramu pre ÚPLNE KAŽDÝ znak v texte (uvidíš ho v štatistike)
                    if (c3 < 256) {
                        stats.addMonogram(c3, stats.getMonogramFreq(c3) + 1.0);
                    }

                    // KĽÚČOVÁ ZMENA: Žiadny hardcode! Pýtame sa priamo našej vyčítanej mapy
                    boolean isOnKeyboard = c3 < 256 && allowedOnKeyboard[c3];

                    if (!isOnKeyboard) {
                        // Znak nie je na klávesnici -> reset rytmu a ideme ďalej
                        idx1 = -1;
                        idx2 = -1;
                        continue; // PRESKAKUJEME výpočet bigramov, trigramov a sendvičov pre tento znak
                    }

                    // KROK C: Zápis pokročilých n-gramov (Sem sa dostanú už len čisté klávesy)
                    // Bigram (c2 -> c3)
                    if (idx2 != -1) {
                        char c2 = (char) idx2;
                        stats.addBigram(c2, c3, stats.getBigramFreq(c2, c3) + 1.0);
                    }

                    // Trigram (c1 -> c2 -> c3) a Skipgram (c1 -> c3)
                    if (idx1 != -1 && idx2 != -1) {
                        char c1 = (char) idx1;
                        char c2 = (char) idx2;

                        // Trigram pre zmeny smeru (c1 -> c2 -> c3)
                        stats.addTrigram(c1, c2, c3, stats.getTrigramFreq(c1, c2, c3) + 1.0);

                        // Skipgram (SFS - Sendvič): Prvý a tretí znak, stredný sa ignoruje
                        stats.addSkipgram(c1, c3, stats.getSkipgramFreq(c1, c3) + 1.0);
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
