package mh.Ofam.layout;

import lombok.Getter;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class TextStats {
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}");

    long wordsTotal = 0;
    long charsTotal = 0;
    @Getter
    Map<String, Long> wordFreqs = new HashMap<>();
    Map<Character, Long> charFreqs = new HashMap<>();
    FastWord[] fastCorpus;

    public static TextStats instance(String text) {
        TextStats textStats = new TextStats();

        // <--- TU SA DEJE ODSTRÁNENIE DIAKRITIKY --->
        String cleanText = stripDiacritics(text).toLowerCase();

        // Rozdelenie na slová (odstránenie interpunkcie pre presnosť slov)
//        String[] words = cleanText.split("[^a-zA-Z0-9/.,;'-]+"); // symboly musia prec
//        String[] words = cleanText.split("[^a-z0-9']+"); // cisla musia prec
//        String[] words = cleanText.toLowerCase().split("[^a-z']+");
        String[] words = cleanText.toLowerCase().split("[^a-z,.']+"); // ciarka bodka nazad

        for (String word : words) {
            if (word.isEmpty()) continue;
            textStats.wordFreqs.put(word, textStats.wordFreqs.getOrDefault(word, 0L) + 1);
            textStats.wordsTotal++;

            for (char ch : word.toCharArray()) {
                // Tu pridáme počítanie samotného znaku:
                textStats.charFreqs.put(ch, textStats.charFreqs.getOrDefault(ch, 0L) + 1L);
                textStats.charsTotal++;
            }

        }

        textStats.printTextReport();

        textStats.fastCorpus = textStats.wordFreqs.entrySet().stream()
                .map(e -> new FastWord(e.getKey(), e.getValue()))
                .toArray(FastWord[]::new);

        return textStats;
    }

    private static String stripDiacritics(String src) {
        if (src == null) return "";
        String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }


    public void printTextReport() {
        System.out.printf("Celkovy pocet slov: %d\n", wordsTotal);

        System.out.printf("Pocet unikatnych slov: %d\n", wordFreqs.size());

        System.out.printf("Celkovy pocet pismen: %d\n", charsTotal);

        printCharsStats();

        printWordsStats(200);
    }

    private void printCharsStats() {
        System.out.println("\n FREKVENCIA JEDNOTLIVÝCH ZNAKOV (Od najpoužívanejšieho)");
        List<Map.Entry<Character, Long>> sortedChars = charFreqs.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .toList();

        int count = 0;
        for (Map.Entry<Character, Long> entry : sortedChars) {
            double charPct = (entry.getValue() * 100.0) / charsTotal;
            System.out.printf("  '%c' : %5.2f%% (%d x)", entry.getKey(), charPct, entry.getValue());

            // Výpis do dvoch stĺpcov pre lepšiu čitateľnosť
            count++;
            if (count % 2 == 0) {
                System.out.println();
            } else {
                System.out.print("    |  ");
            }
        }
        if (count % 2 != 0) System.out.println();

    }

    private void printWordsStats(int size) {
        System.out.println("\n TOP " + size + " NAJČASTEJŠÍCH SLOV");
        System.out.println("--------------------------------------------------------------------------------");

        List<Map.Entry<String, Long>> sortedWords = wordFreqs.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(size)
                .toList();

// Vypíšeme top rebricek slov v 2 stĺpcoch pre maximálnu prehľadnosť
        for (int i = 0; i < sortedWords.size(); i += 2) {
            Map.Entry<String, Long> e1 = sortedWords.get(i);
            double pct1 = (e1.getValue() * 100.0) / wordsTotal;
            String word1 = "'" + e1.getKey() + "'";
            String col1 = String.format("  %3d. %-15s : %5.2f%% (%d x)", (i + 1), word1, pct1, e1.getValue());

            String col2 = "";
            if (i + 1 < sortedWords.size()) {
                Map.Entry<String, Long> e2 = sortedWords.get(i + 1);
                double pct2 = (e2.getValue() * 100.0) / wordsTotal;
                String word2 = "'" + e2.getKey() + "'";
                col2 = String.format("    |    %3d. %-15s : %5.2f%% (%d x)", (i + 2), word2, pct2, e2.getValue());
            }

            System.out.println(col1 + col2);
        }
        System.out.println("--------------------------------------------------------------------------------");
    }

}
