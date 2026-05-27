package mh.layout.analyzer;

import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.NGramStats;
import mh.layout.stats.MetricType;
import mh.layout.topology.Finger;
import mh.layout.topology.KeyboardTopology;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class LayoutAnalyzer {

    private LayoutAnalyzer() {}

    /**
     * Dátový kontajner, ktorý zachytí celý report a vie ho zapísať na disk.
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

        public void saveToFile(String filePath) throws IOException {
            try (PrintWriter writer = new PrintWriter(
                    new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(filePath, true),
                            StandardCharsets.UTF_8))) {
                writer.print(output.toString());
                writer.println("\n\n# ====================================================================\n\n");
            }
        }
    }

    public static AnalysisReport generateReport(char[] matrix, String layoutName, NGramStats statsSK, NGramStats statsEN,
                                                Map<String, Double> dictSK, Map<String, Double> dictEN,
                                                Weights weights, double skRatio) {

        AnalysisReport report = new AnalysisReport();

        // 1. Spustíme bleskové jadro pre reálne čísla oboch jazykov
        LayoutStats skStats = Evaluator.evaluate(matrix, statsSK, weights);
        LayoutStats enStats = Evaluator.evaluate(matrix, statsEN, weights);

        double enRatio = 1.0 - skRatio;
        double combinedScore = (skStats.getScore() * skRatio) + (enStats.getScore() * enRatio);

        // --- HLAVIČKA REPORTU ---
        report.append("====================================================================\n");
        report.append("📊      BIOMECHANICKÝ AUDIT REKORDNÉHO LAYOUTU: %-16s  📊\n", layoutName);
        report.append("====================================================================\n");
        report.append("👑 KOMBINOVANÉ BILINGVÁLNE ELITE SKÓRE: %.4f\n", combinedScore);
        report.append("🇸🇰 Slovenská vetva: %.4f (Váha: %.0f%%)\n", skStats.getScore(), skRatio * 100);
        report.append("🇬🇧 Anglická vetva:   %.4f (Váha: %.0f%%)\n", enStats.getScore(), enRatio * 100);
        report.append("--------------------------------------------------------------------\n");

        // --- TABUĽKA REÁLNYCH METRÍK ---
        report.append("\n🧠 BIOMECHANICKÉ UKAZOVATELE (Frekvencie výskytu v %%):\n");
        report.append(String.format("%-25s | %-18s | %-18s\n", "Metrika", "Slovenčina (SK)", "Angličtina (EN)"));
        report.append("--------------------------------------------------------------------\n");
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "SFB (Rovnaký prst)", skStats.getMetricPercent(MetricType.SFB), enStats.getMetricPercent(MetricType.SFB)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "SFS (Sendviče)", skStats.getMetricPercent(MetricType.SFS), enStats.getMetricPercent(MetricType.SFS)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "ALT (Striedanie rúk)", skStats.getMetricPercent(MetricType.ALTERNATION), enStats.getMetricPercent(MetricType.ALTERNATION)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Roll-In (Valenie dnu)", skStats.getMetricPercent(MetricType.ROLL_IN), enStats.getMetricPercent(MetricType.ROLL_IN)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Roll-Out (Valenie von)", skStats.getMetricPercent(MetricType.ROLL_OUT), enStats.getMetricPercent(MetricType.ROLL_OUT)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Scissors (Nožnice)", skStats.getMetricPercent(MetricType.SCISSORS), enStats.getMetricPercent(MetricType.SCISSORS)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Redirects (Otočky)", skStats.getMetricPercent(MetricType.REDIRECTS), enStats.getMetricPercent(MetricType.REDIRECTS)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "LSB (Bočné uličky)", skStats.getMetricPercent(MetricType.LSB), enStats.getMetricPercent(MetricType.LSB)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "LHB (Záťaž malíčkov)", skStats.getMetricPercent(MetricType.LHB), enStats.getMetricPercent(MetricType.LHB)));
        report.append(String.format("%-25s | %-18.4f | %-18.4f\n", "Avg Stretch Index", skStats.getMetricPercent(MetricType.EFFORT), enStats.getMetricPercent(MetricType.EFFORT)));
        report.append("--------------------------------------------------------------------\n");

        // --- ZAŤAŽENIE JEDNOTLIVÝCH PRSTOV ---
        report.append("\n🖐️ DISTRIBÚCIA ZAŤAŽENIA PRSTOV (v %%):\n");
        report.append(String.format("%-12s | %-6s | %-6s | %-6s | %-6s || %-6s | %-6s | %-6s | %-6s\n",
                "Jazyk", "L_Mal", "L_Prs", "L_Pro", "L_Uka", "R_Uka", "R_Pro", "R_Prs", "R_Mal"));
        report.append("--------------------------------------------------------------------\n");
        report.append(String.format("%-12s | %6.2f | %6.2f | %6.2f | %6.2f || %6.2f | %6.2f | %6.2f | %6.2f\n", "Slovenčina",
                skStats.getFingerPercent(Finger.LP.ordinal()), skStats.getFingerPercent(Finger.LR.ordinal()),
                skStats.getFingerPercent(Finger.LM.ordinal()), skStats.getFingerPercent(Finger.LI.ordinal()),
                skStats.getFingerPercent(Finger.RI.ordinal()), skStats.getFingerPercent(Finger.RM.ordinal()),
                skStats.getFingerPercent(Finger.RR.ordinal()), skStats.getFingerPercent(Finger.RP.ordinal())));
        report.append(String.format("%-12s | %6.2f | %6.2f | %6.2f | %6.2f || %6.2f | %6.2f | %6.2f | %6.2f\n", "Angličtina",
                enStats.getFingerPercent(Finger.LP.ordinal()), enStats.getFingerPercent(Finger.LR.ordinal()),
                enStats.getFingerPercent(Finger.LM.ordinal()), enStats.getFingerPercent(Finger.LI.ordinal()),
                enStats.getFingerPercent(Finger.RI.ordinal()), enStats.getFingerPercent(Finger.RM.ordinal()),
                enStats.getFingerPercent(Finger.RR.ordinal()), enStats.getFingerPercent(Finger.RP.ordinal())));

        // 2. SPRACOVANIE DIGITÁLNEHO RÖNTGENU SLOVNÍKOV
        boolean[] presentOnKeyboard = new boolean[256];
        int[] charToHand = new int[256];
        int[] charToRow = new int[256];
        int[] charToCol = new int[256];
        int[] charToFinger = new int[256];

        // 🚀 FIX: Vyťahujeme anatómiu cez bitové masky z POS_INFO, presne podľa novej topológie
        final int[] posInfo = KeyboardTopology.POS_INFO;
        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            if (c < 256) {
                presentOnKeyboard[c] = true;
                int info = posInfo[i];
                charToHand[c]   = (info >> 9) & 0x1;
                charToRow[c]    = (info >> 7) & 0x3;
                charToCol[c]    = (info >> 3) & 0xF;
                charToFinger[c] = info & 0x7;
            }
        }

        report.append("\n====================================================================\n");
        report.append("🔍       HĹBKOVÁ ANALÝZA SLOVNÍKOV (Top 10 najhorších slov)        \n");
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
        report.append("\n🌍 Vetva: %s\n", label);
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
