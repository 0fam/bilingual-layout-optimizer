package mh.layout.analyzer.stats;

import mh.layout.core.topology.KeyboardTopology;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public final class RunStatisticsAnalyzer {

    /**
     * Zoberie finálny layout a mapu reálnych slov z korpusu a vytvorí presný rozpad jednoručných behov.
     * @param matrix layout (char[])
     * @param wordDictionary Mapa slov a ich reálnych frekvencií v texte Map<String, Double>
     */
    public static void analyzeAndPrintRuns(char[] matrix, Map<String, Double> wordDictionary, String label) {
        // Inicializujeme pole hodnotou -1 (Znak neexistuje na klávesnici)
        int[] charToHand = new int[256];
        Arrays.fill(charToHand, -1);

        // Vyčítame reálne ruky pre znaky, ktoré na klávesnici reálne sú
        for (int i = 0; i < 30; i++) {
            charToHand[matrix[i]] = KeyboardTopology.POS_TO_HAND[i];
        }

        Map<Integer, Integer> countByLength = new TreeMap<>();
        Map<Integer, Double> freqByLength = new TreeMap<>();
        int totalOneHandedWords = 0;

        for (Map.Entry<String, Double> entry : wordDictionary.entrySet()) {
            String word = entry.getKey();
            double freq = entry.getValue();

            if (word.length() < 4) continue;

            if (isPureOneHandedWord(word, charToHand)) {
                int len = word.length();
                countByLength.put(len, countByLength.getOrDefault(len, 0) + 1);
                freqByLength.put(len, freqByLength.getOrDefault(len, 0.0) + freq);
                totalOneHandedWords++;
            }
        }

        // --- VYTLAČENIE PARÁDNEHO REPORTU PRE ČLOVEKA ---
        System.out.println("\n=== 🎯 BILINGVÁLNA ŠTATISTIKA JEDNORUČNÝCH BEHOV ===");
        System.out.println("Celkový počet unikátnych jednoručných slov (dĺžka >= 4): " + totalOneHandedWords);
        System.out.println("----------------------------------------------------------------");
        System.out.printf("%-10s | %-15s | %-15s\n", "Dĺžka slova", "Počet unikátnych", "Celková frekvencia %");
        System.out.println("----------------------------------------------------------------");

        for (int len : countByLength.keySet()) {
            System.out.printf("%-10d | %-15d | %-15.4f%%\n",
                    len,
                    countByLength.get(len),
                    freqByLength.get(len)
            );
        }
    }

    /**
     * Pomocná kontrola, či celé slovo odklepeš výhradne jednou rukou.
     */
    private static boolean isPureOneHandedWord(String word, int[] charToHand) {
        if (word.isEmpty()) return false;

        // Zistíme ruku prvého znaku
        int firstCharHand = charToHand[word.charAt(0)];
        if (firstCharHand == -1) return false; // Ak prvý znak nie je na klávesnici, nie je to validný beh

        for (int i = 1; i < word.length(); i++) {
            int currentHand = charToHand[word.charAt(i)];

            // Ak znak na klávesnici nie je (-1), alebo sa ruka v slove reálne zmenila
            if (currentHand == -1 || currentHand != firstCharHand) {
                return false;
            }
        }
        return true;
    }
}
