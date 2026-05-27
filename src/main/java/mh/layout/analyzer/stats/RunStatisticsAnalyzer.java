package mh.layout.analyzer.stats;

import mh.layout.topology.KeyboardTopology;
import java.util.Arrays;
import java.util.Map;

public final class RunStatisticsAnalyzer {

    /**
     * Zoberie finálny layout a mapu reálnych slov z korpusu a vytvorí presný rozpad jednoručných behov.
     * @param matrix layout (char[])
     * @param wordDictionary Mapa slov a ich reálnych frekvencií v texte Map<String, Double>
     */
    public static void analyzeAndPrintRuns(char[] matrix, Map<String, Double> wordDictionary, String label) {
        // Fixné lookup pole pre priradenie ruky k znaku (0=Ľavá, 1=Pravá, -1=Nie je na klávesnici)
        int[] charToHand = new int[256];
        Arrays.fill(charToHand, -1);

        // Vyčítame reálne ruky pre znaky, ktoré na klávesnici reálne sú
        for (int i = 0; i < 30; i++) {
            charToHand[matrix[i]] = KeyboardTopology.POS_TO_HAND[i];
        }

        // KĽÚČOVÁ ZMENA: Žiadne mapy! Použijeme fixné polia pre dĺžky slov 0 až 31
        long[] countByLength = new long[32];
        double[] freqByLength = new double[32];

        long totalOneHandedWords = 0;

        // Prechádzame slovník
        for (Map.Entry<String, Double> entry : wordDictionary.entrySet()) {
            String word = entry.getKey();
            double freq = entry.getValue();

            int len = word.length();
            if (len < 4 || len > 31) continue; // Ignorujeme príliš krátke alebo extrémne dlhé anomálie

            if (isPureOneHandedWord(word, charToHand)) {
                countByLength[len]++;          // Inkrementujeme počet unikátnych slov danej dĺžky
                freqByLength[len] += freq;     // Akumulujeme reálnu frekvenciu z textu
                totalOneHandedWords++;
            }
        }

        // --- VÝPIS ČISTÉHO REPORTU ---
        System.out.println("\n=== 🎯 ŠTATISTIKA JEDNORUČNÝCH BEHOV PRE: " + label + " ===");
        System.out.println("Celkový počet unikátnych jednoručných slov (dĺžka >= 4): " + totalOneHandedWords);
        System.out.println("----------------------------------------------------------------");
        System.out.printf("%-10s | %-15s | %-15s\n", "Dĺžka slova", "Počet unikátnych", "Celková frekvencia");
        System.out.println("----------------------------------------------------------------");

        // Prebehneme fixné pole od indexu 4 navrch
        for (int len = 4; len < 32; len++) {
            if (countByLength[len] > 0) {
                System.out.printf("%-10d | %-15d | %-15.4f\n",
                        len,
                        countByLength[len],
                        freqByLength[len]
                );
            }
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
