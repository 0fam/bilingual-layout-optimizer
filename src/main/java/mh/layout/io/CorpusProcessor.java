package mh.layout.io;

import mh.layout.stats.NGramStats;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class CorpusProcessor {

    private CorpusProcessor() {}

    /**
     * Načíta korpus, odmasťuje diakritiku a plní štatistiky pre jeden konkrétny jazyk.
     * * @param filePath Cesta k súboru korpusu (napr. data/slovak_corpus.txt)
     * @param stats    Samostatná dátová schránka pre tento konkrétny jazyk
     * @param layout   Aktuálny štartovací layout, z ktorého dynamicky vyčítame povolené symboly
     * @return Čistý slovník "písacích kusov" rozdelených medzerami pre záverečný report Analyzátora
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

        // --- 2. ČÍTANIE SÚBORU RIADOK PO RIADKU ---
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                // Prevod na veľké písmená a odstránenie dĺžňov/mäkčeňov
                String cleanedLine = normalizeLine(line);

                // Zber kusov textu pre Analyzátor (sekanie podľa medzier)
                String[] chunks = cleanedLine.split("\\s+");
                for (String chunk : chunks) {
                    if (chunk.length() >= 4) { // Krátke kusy pre štatistiku behov preskakujeme
                        wordDictionary.merge(chunk, 1.0, Double::sum);
                    }
                }

                // --- 4. KĹZAVÉ OKNO PRE N-GRAMY (Čisté prírastky) ---
                int idx1 = -1; // Pred-predchodca (c1)
                int idx2 = -1; // Predchodca (c2)

                for (int i = 0; i < cleanedLine.length(); i++) {
                    char c3 = cleanedLine.charAt(i);

                    // Monogramy: Každý reálny ASCII znak v texte zrátame pre potreby celkovej štatistiky
                    if (c3 < 256) {
                        stats.incrementMonogram(c3);
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
                        stats.incrementBigram((char) idx2, c3);
                    }

                    // Trigramy (c1 -> c2 -> c3) a Skipgramy (c1 -> c3) pre sendviče (SFS)
                    if (idx1 != -1 && idx2 != -1) {
                        stats.incrementTrigram((char) idx1, (char) idx2, c3);
                        stats.incrementSkipgram((char) idx1, c3);
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
     * Bleskové odstránenie slovenskej diakritiky v O(N) čase bez zbytočnej alokácie objektov.
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
