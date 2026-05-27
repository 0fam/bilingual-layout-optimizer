package mh.layout.analyzer;

import mh.layout.core.evaluator.Evaluator;
import mh.layout.core.evaluator.Weights;
import mh.layout.core.stats.LayoutStats;
import mh.layout.core.stats.NGramStats;
import mh.layout.core.topology.KeyboardTopology;
import java.util.Map;
import java.util.TreeMap;

public final class LayoutAnalyzer {

    /**
     * Zoberie finálny layout, samostatné štatistiky a slovníky pre oba jazyky
     * a vytlačí do konzoly kompletný biomechanický audit klávesnice.
     */
    public static void printCompleteReport(char[] matrix, NGramStats statsSK, NGramStats statsEN,
                                           Map<String, Double> dictSK, Map<String, Double> dictEN,
                                           Weights weights, double skRatio) {

        // 1. Spustíme nezávislé vyhodnotenie pre oba jazyky v našom plochom jadre
        LayoutStats skStats = Evaluator.evaluate(matrix, statsSK, weights);
        LayoutStats enStats = Evaluator.evaluate(matrix, statsEN, weights);

        double enRatio = 1.0 - skRatio;
        double combinedScore = (skStats.getEliteScore() * skRatio) + (enStats.getEliteScore() * enRatio);

        System.out.println("\n================================================================");
        System.out.println("📊   FINÁLNY BIOMECHANICKÝ AUDIT BILINGVÁLNEHO LAYOUTU   📊");
        System.out.println("================================================================");
        System.out.printf("👑 KOMBINOVANÉ BILINGVÁLNE ELITE SKÓRE: %.4f\n", combinedScore);
        System.out.printf("🇸🇰 Slovenská vetva: %.4f (Váha: %.0f%%)\n", skStats.getEliteScore(), skRatio * 100);
        System.out.printf("🇬🇧 Anglická vetva:   %.4f (Váha: %.0f%%)\n", enStats.getEliteScore(), enRatio * 100);
        System.out.println("----------------------------------------------------------------");

        // 2. VYTLAČENIE ANATOMICKÝCH METRÍK (Porovnanie jazykov vedľa seba)
        System.out.println("\n🧠 BIOMECHANICKÉ UKAZOVATELE (Frekvencie výskytu):");
        System.out.printf("%-15s | %-15s | %-15s\n", "Metrika", "Slovenčina (SK)", "Angličtina (EN)");
        System.out.println("----------------------------------------------------------------");
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "SFB (Rovnaký prst)", skStats.getSfb(), enStats.getSfb());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "SFS (Sendviče)", skStats.getSfs(), enStats.getSfs());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "ALT (Striedanie)", skStats.getAlt(), enStats.getAlt());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "Roll-In (Dnu)", skStats.getRollIn(), enStats.getRollIn());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "Roll-Out (Von)", skStats.getRollOut(), enStats.getRollOut());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "Scissors (Nožnice)", skStats.getScissors(), enStats.getScissors());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "Redirects (Otočky)", skStats.getRedirects(), enStats.getRedirects());
        System.out.printf("%-15s | %-15.4f | %-15.4f\n", "Peak Finger Load", skStats.getFingerLoad(), enStats.getFingerLoad());

        // 3. ANALÝZA JEDNORUČNÝCH BEHOV CEZ ODLOŽENÉ SLOVNÍKY (To, čo sme chceli!)
        System.out.println("\n🏃 ŠTATISTIKA ČISTÝCH JEDNORUČNÝCH SLOV (Dĺžka >= 4):");
        printOneHandedWordsStats(matrix, dictSK, "Slovenčina (SK)");
        printOneHandedWordsStats(matrix, dictEN, "Angličtina (EN)");
    }

    /**
     * Prebehne reálny slovník textu a vytvorí rozpad jednoručných slov podľa dĺžky.
     */
    private static void printOneHandedWordsStats(char[] matrix, Map<String, Double> wordDictionary, String label) {
        int[] charToHand = new int[256];
        for (int i = 0; i < 30; i++) {
            charToHand[matrix[i]] = KeyboardTopology.POS_TO_HAND[i];
        }

        Map<Integer, Integer> countByLength = new TreeMap<>();
        int totalWords = 0;

        for (String word : wordDictionary.keySet()) {
            if (isPureOneHandedWord(word, charToHand)) {
                int len = word.length();
                countByLength.put(len, countByLength.getOrDefault(len, 0) + 1);
                totalWords++;
            }
        }

        System.out.printf("\n📋 %s (Celkovo unikátnych jednoručných slov: %d):\n", label, totalWords);
        if (totalWords == 0) {
            System.out.println("  Žiadne dlhé jednoručné slová! Dokonalé rozdelenie práce.");
            return;
        }

        for (Map.Entry<Integer, Integer> entry : countByLength.entrySet()) {
            System.out.printf("  • Dĺžka %d znakov: %d unikátnych slov\n", entry.getKey(), entry.getValue());
        }
    }

    private static boolean isPureOneHandedWord(String word, int[] charToHand) {
        if (word.isEmpty()) return false;

        // Zistíme ruku prvého znaku (0 = Ľavá, 1 = Pravá)
        int initialHand = -1;

        // Nájdeme prvý platný znak z našej klávesnice, aby sme vedeli, ktorou rukou slovo začína
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c < 256 && (c >= 'A' && c <= 'Z' || c == ',' || c == '.' || c == '?' || c == '-')) {
                initialHand = charToHand[c];
                break;
            }
        }
        if (initialHand == -1) return false;

        // Kontrolujeme, či všetky ostatné platné znaky v slove sedia na tej istej ruke
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            // Ak je znak na klávesnici a stláča ho druhá ruka, plynulý beh sa rozbil
            if (c < 256 && (c >= 'A' && c <= 'Z' || c == ',' || c == '.' || c == '?' || c == '-')) {
                if (charToHand[c] != initialHand) {
                    return false;
                }
            }
        }
        return true;
    }
}
