package mh.layout.analyzer;

import mh.layout.stats.Evaluator;
import mh.layout.stats.Weights;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.NGramStats;
import mh.layout.stats.MetricType;
import mh.layout.topology.KeyboardTopology;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class LayoutAnalyzer {

    /**
     * Dátový kontajner, ktorý zachytí celý report.
     * Oslobodzuje analyzátor od priameho tlačenia do konzoly.
     */
    public static final class AnalysisReport {
        private final StringBuilder output = new StringBuilder();

        void append(String format, Object... args) {
            output.append(String.format(format, args));
        }

        @Override
        public String toString() {
            return output.toString();
        }

        /**
         * Bleskové uloženie celého biomechanického auditu do súboru na disk.
         */
        public void saveToFile(String filePath) throws IOException {
            // Použitím FileOutputStream s parametrom 'true' povieme Jave, aby text pridávala na koniec (append)
            try (PrintWriter writer = new PrintWriter(
                    new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(filePath, true),
                            StandardCharsets.UTF_8))) {
                writer.print(output.toString());
                writer.println("\n\n# ====================================================================\n\n");
                // Tento riadok vyššie ti vizuálne oddelí staré audity od nových v jednom súbore, nech sa ti to lepšie číta.
            }
        }
    }

    public static AnalysisReport generateReport(char[] matrix, NGramStats statsSK, NGramStats statsEN,
                                                Map<String, Double> dictSK, Map<String, Double> dictEN,
                                                Weights weights, double skRatio) {

        AnalysisReport report = new AnalysisReport();

        // 1. Spustíme bleskové jadro pre globálne čísla
        LayoutStats skStats = Evaluator.evaluate(matrix, statsSK, weights);
        LayoutStats enStats = Evaluator.evaluate(matrix, statsEN, weights);

        double enRatio = 1.0 - skRatio;
        double combinedScore = (skStats.getScore() * skRatio) + (enStats.getScore() * enRatio);

        // Zistenie špičkovej záťaže prstov
        double skMaxFinger = 0;
        double enMaxFinger = 0;
        MetricType[] fingers = {
                MetricType.L_PINKY, MetricType.L_RING, MetricType.L_MIDDLE, MetricType.L_INDEX,
                MetricType.R_INDEX, MetricType.R_MIDDLE, MetricType.R_RING, MetricType.R_PINKY
        };
        for (MetricType f : fingers) {
            skMaxFinger = Math.max(skMaxFinger, skStats.get(f));
            enMaxFinger = Math.max(enMaxFinger, enStats.get(f));
        }

        // --- HLAVIČKA REPORTU ---
        report.append("====================================================================\n");
        report.append("📊      FINÁLNY BIOMECHANICKÝ AUDIT BILINGVÁLNEHO LAYOUTU       📊\n");
        report.append("====================================================================\n");
        report.append("👑 KOMBINOVANÉ BILINGVÁLNE ELITE SKÓRE: %.4f\n", combinedScore);
        report.append("🇸🇰 Slovenská vetva: %.4f (Váha: %.0f%%)\n", skStats.getScore(), skRatio * 100);
        report.append("🇬🇧 Anglická vetva:   %.4f (Váha: %.0f%%)\n", enStats.getScore(), enRatio * 100);
        report.append("--------------------------------------------------------------------\n");

        // --- TABUĽKA METRÍK ---
        report.append("\n🧠 BIOMECHANICKÉ UKAZOVATELE (Frekvencie výskytu v %%):\n");
        report.append(String.format("%-25s | %-18s | %-18s\n", "Metrika", "Slovenčina (SK)", "Angličtina (EN)"));
        report.append("--------------------------------------------------------------------\n");
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "SFB (Rovnaký prst)", skStats.get(MetricType.SFB), enStats.get(MetricType.SFB)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "SFS (Sendviče)", skStats.get(MetricType.SFS), enStats.get(MetricType.SFS)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "ALT (Striedanie rúk)", skStats.get(MetricType.ALTERNATION), enStats.get(MetricType.ALTERNATION)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Roll-In (Valenie dnu)", skStats.get(MetricType.ROLL_IN), enStats.get(MetricType.ROLL_IN)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Roll-Out (Valenie von)", skStats.get(MetricType.ROLL_OUT), enStats.get(MetricType.ROLL_OUT)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Scissors (Nožnice)", skStats.get(MetricType.SCISSORS), enStats.get(MetricType.SCISSORS)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Redirects (Otočky)", skStats.get(MetricType.REDIRECTS), enStats.get(MetricType.REDIRECTS)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "LSB (Bočné uličky)", skStats.get(MetricType.LSB), enStats.get(MetricType.LSB)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "LHB (Záťaž malíčkov)", skStats.get(MetricType.LHB), enStats.get(MetricType.LHB)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Avg Stretch Index", skStats.get(MetricType.STRETCH), enStats.get(MetricType.STRETCH)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Peak Finger Load", skMaxFinger, enMaxFinger));

        // 2. SPRACOVANIE DIGITÁLNEHO RÖNTGENU SLOVNÍKOV (Top 10 hriešnici)
        // Vyrobíme si rýchle lookup tabuľky mapované priamo na Totem matrix
        boolean[] presentOnKeyboard = new boolean[256];
        int[] charToHand = new int[256];
        int[] charToRow = new int[256];
        int[] charToCol = new int[256];
        int[] charToFinger = new int[256];

        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            if (c < 256) {
                presentOnKeyboard[c] = true;
                charToHand[c] = KeyboardTopology.POS_TO_HAND[i];
                charToRow[c] = KeyboardTopology.POS_TO_ROW[i];
                charToCol[c] = i % 10; // Čistá matematika, stĺpec 0 až 9
                charToFinger[c] = KeyboardTopology.POS_TO_FINGER[i];
            }
        }

        report.append("\n====================================================================\n");
        report.append("🔍       HLBKOVÁ ANALÝZA SLOVNÍKOV (Top 10 najhorších slov)        \n");
        report.append("====================================================================\n");

        analyzeLanguageOffenders(report, dictSK, "SLOVENČINA (SK)", presentOnKeyboard, charToHand, charToRow, charToCol, charToFinger);
        analyzeLanguageOffenders(report, dictEN, "ANGLIČTINA (EN)", presentOnKeyboard, charToHand, charToRow, charToCol, charToFinger);

        return report;
    }

    private static void analyzeLanguageOffenders(AnalysisReport report, Map<String, Double> dictionary, String label,
                                                 boolean[] present, int[] hands, int[] rows, int[] cols, int[] fingers) {

        Map<String, Double> sfbWords = new HashMap<>();
        Map<String, Double> stretchWords = new HashMap<>();
        Map<String, Double> longRunWords = new HashMap<>();

        int[] validWordKeys = new int[128]; // Recyklovateľné pole indexov pre simuláciu slova

        for (Map.Entry<String, Double> entry : dictionary.entrySet()) {
            String origWord = entry.getKey();
            double freq = entry.getValue();

            // SITO: Prefiltrujeme len čisté znaky, ktoré klávesnica reálne pozná (bez diakritiky)
            int len = 0;
            for (int i = 0; i < origWord.length(); i++) {
                char c = origWord.charAt(i);
                if (c < 256 && present[c]) {
                    validWordKeys[len++] = c;
                }
            }
            if (len == 0) continue;

            double wordSfb = 0;
            double wordStretch = 0;
            int maxRun = 0;
            int currentRun = 0;
            int lastHand = -1;

            // SIMULÁCIA PÍSANIA SLOVA
            for (int i = 0; i < len; i++) {
                int c = validWordKeys[i];
                int h = hands[c];
                int r = rows[c];
                int col = cols[c];
                int f = fingers[c];

                // A. Výpočet asymetrického stretchu slova
                if (col == 4 || col == 5) {
                    double w = 0.1;
                    if (r == 0) w = 2.0;
                    else if (r == 2) w = 1.0;
                    wordStretch += w;
                }

                // B. Výpočet SFB v slove
                if (i < len - 1) {
                    int nextC = validWordKeys[i + 1];
                    if (hands[c] == hands[nextC] && fingers[c] == fingers[nextC] && c != nextC) {
                        wordSfb += 1.0;
                    }
                }

                // C. Výpočet jednoručných behov (Runs)
                if (h == lastHand) {
                    currentRun++;
                } else {
                    maxRun = Math.max(maxRun, currentRun);
                    currentRun = 1;
                    lastHand = h;
                }
            }
            maxRun = Math.max(maxRun, currentRun);

            // Ak slovo vyvolalo hriech, zaznamenáme jeho váhu (frekvencia * prehrešok v slove)
            if (wordSfb > 0) sfbWords.put(origWord, wordSfb * freq);
            if (wordStretch > 0) stretchWords.put(origWord, wordStretch * freq);
            if (maxRun >= 4) longRunWords.put(origWord, (double) maxRun * freq);
        }

        // Tlač rebríčkov do reportu
        report.append("\n🇸🇰 Vetva: %s\n", label);
        report.append("-------");
        report.append("\n🔥 Top 10 kmitavých slov (SFB kŕč):\n");
        printTop10(report, sfbWords);

        report.append("\n💥 Top 10 slov natiahnutých do stredu (Stretch):\n");
        printTop10(report, stretchWords);

        report.append("\n🏃 Top 10 únavných jednoručných maratónov (Runs >= 4):\n");
        printTop10(report, longRunWords);
        report.append("--------------------------------------------------------------------\n");
    }

    private static void printTop10(AnalysisReport report, Map<String, Double> map) {
        List<Map.Entry<String, Double>> sorted = map.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            report.append("  • Žiadne problémové výskyty.\n");
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            report.append("  %2d. %-18s (Vážený dopad: %,.0f)\n", i + 1, entry.getKey(), entry.getValue());
        }
    }
}
