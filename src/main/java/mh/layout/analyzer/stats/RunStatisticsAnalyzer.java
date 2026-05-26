package mh.layout.analyzer.stats;

import mh.layout.core.topology.KeyboardTopology;
import java.util.Map;
import java.util.TreeMap;

public final class RunStatisticsAnalyzer {

    /**
     * Zoberie finálny layout a mapu reálnych slov z korpusu a vytvorí presný rozpad jednoručných behov.
     * * @param matrix Víťazný layout (char[])
     * @param wordDictionary Mapa slov a ich reálnych frekvencií v texte Map<String, Double>
     */
    public static void analyzeAndPrintRuns(char[] matrix, Map<String, Double> wordDictionary) {
        // Rýchly lookup: pre každé ASCII písmeno chceme vedieť, na ktorej ruke sedí (0=Ľavá, 1=Pravá)
        int[] charToHand = new int[256];
        for (int i = 0; i < 30; i++) {
            charToHand[matrix[i]] = KeyboardTopology.POS_TO_HAND[i];
        }

        // TreeMap nám automaticky zoradí štatistiku podľa dĺžky slova (4, 5, 6...)
        Map<Integer, Integer> countByLength = new TreeMap<>();
        Map<Integer, Double> freqByLength = new TreeMap<>();

        int totalOneHandedWords = 0;

        // Prechádzame reálne slová z korpusu (napr. "stewardesses", "the", "cat"...)
        for (Map.Entry<String, Double> entry : wordDictionary.entrySet()) {
            String word = entry.getKey();
            double freq = entry.getValue();

            if (word.length() < 4) continue; // Krátke slová (the, and, sk) nás nezaujímajú, tie sú v pohode

            if (isPureOneHandedWord(word, charToHand)) {
                int len = word.length();

                // Zapíšeme do štatistiky počtu unikátnych slov
                countByLength.put(len, countByLength.getOrDefault(len, 0) + 1);
                // Zapíšeme reálny výskyt (váhu) v texte
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

        int firstCharHand = charToHand[word.charAt(0)];

        for (int i = 1; i < word.length(); i++) {
            if (charToHand[word.charAt(i)] != firstCharHand) {
                return false; // Ruky sa vystriedali, toto nie je čistý beh!
            }
        }
        return true; // Celé slovo padlo na jednu ruku
    }
}
