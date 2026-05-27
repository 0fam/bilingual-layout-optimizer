package mh.layout.io;

import mh.layout.stats.NGramStats;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class CorpusProcessor {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private CorpusProcessor() {}

    /**
     * Načíta súbor, očistí ho a naplní objekt NGramStats n-gramovými frekvenciami.
     */
    public static NGramStats processFile(String filePath) throws IOException {
        NGramStats stats = new NGramStats();
        StringBuilder sb = new StringBuilder();

        // 1. Načítanie celého textu z disku
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(" "); // Spájame riadky medzerou
            }
        }

        // 2. Čistenie textu (Veľké písmená + odstránenie diakritiky)
        String cleanedText = cleanText(sb.toString());

        // 3. Počítanie n-gramov
        int length = cleanedText.length();
        if (length == 0) {
            return stats;
        }

        // A. Monogramy
        for (int i = 0; i < length; i++) {
            stats.incrementMonogram(cleanedText.charAt(i));
        }

        // B. Bigramy
        for (int i = 0; i < length - 1; i++) {
            stats.incrementBigram(cleanedText.charAt(i), cleanedText.charAt(i + 1));
        }

        // C. Skipgramy (Písmeno na pozícii i a písmeno na pozícii i+2 - dôležité pre SFS)
        for (int i = 0; i < length - 2; i++) {
            stats.incrementSkipgram(cleanedText.charAt(i), cleanedText.charAt(i + 2));
        }

        // D. Trigramy
        for (int i = 0; i < length - 2; i++) {
            stats.incrementTrigram(cleanedText.charAt(i), cleanedText.charAt(i + 1), cleanedText.charAt(i + 2));
        }

        return stats;
    }

    /**
     * Pomocná metóda, ktorá prevedie text na UPPERCASE, odstráni mäkčene/dĺžne
     * a povolí len čistú abecedu a základnú interpunkciu.
     */
    private static String cleanText(String src) {
        if (src == null) return "";

        // Prevod na veľké písmená
        String upper = src.toUpperCase();

        // Odstránenie diakritiky (z Č sa stane C, z Ô sa stane O atď.)
        String normalized = Normalizer.normalize(upper, Normalizer.Form.NFD);
        String noDiacritics = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");

        // Nahradíme špeciálne slovenské znaky, ktoré Normalizer občas minie (napr. ľ, Ľ, ß)
        noDiacritics = noDiacritics.replace("Ľ", "L").replace("ß", "SS");

        // Prefiltrujeme text: necháme len A-Z, medzeru a základné interpunkčné znaky (. , ? ! - ;)
        StringBuilder result = new StringBuilder(noDiacritics.length());
        for (int i = 0; i < noDiacritics.length(); i++) {
            char c = noDiacritics.charAt(i);

            // Povolené znaky, ktoré korešpondujú s tvojou 30-klávesovou mriežkou
            if ((c >= 'A' && c <= 'Z') || c == ' ' || c == '.' || c == ',' || c == '?' || c == '!' || c == '-' || c == ';') {
                result.append(c);
            }
        }

        // Nahradíme viacnásobné medzery jednou jedinou, nech nám to neskresľuje štatistiky
        return result.toString().replaceAll("\\s+", " ");
    }
}
