package mh.layout;

import mh.layout.analyzer.LayoutAnalyzer;
import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.io.CorpusProcessor;
import mh.layout.io.LayoutLoader;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.MetricType;
import mh.layout.stats.NGramStats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LayoutAnalysisEngine {
    private final Weights weights = new Weights();
    private final NGramStats statsSK = new NGramStats();
    private final NGramStats statsEN = new NGramStats();

    private Map<String, Double> dictSK;
    private Map<String, Double> dictEN;

    private final double slovakRatio = 0.80;

    // Vnútorný pomocný kontajner pre potreby zoradenia výsledkov
    private record AnalyzedLayout(
            String name,
            char[] matrix,
            LayoutStats skStats,
            LayoutStats enStats,
            double combinedScore
    ) {}

    public void runStandalone(String targetPath) {
        System.out.println("========================================================================================");
        System.out.println("               SPÚŠŤAM STANDALONE BIOMECHANICKÚ ANALÝZU LAYOUTOV                        ");
        System.out.println("========================================================================================\n");

        // 1. Načítanie invariantných štatistík textu (Slovníky pre röntgen prstov)
        System.out.println("📥 Načítavam jazykové korpusy a slovníky slov...");
        try {
            this.dictSK = CorpusProcessor.processFile("c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus.txt", statsSK);
            System.out.println("✅ Slovenský korpus úspešne spracovaný.");

            this.dictEN = CorpusProcessor.processFile("c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus_en.txt", statsEN);
            System.out.println("✅ Anglický korpus úspešne spracovaný.\n");
        } catch (IOException e) {
            System.err.println("❌ KRITICKÁ CHYBA: Nepodarilo sa načítať korpusy: " + e.getMessage());
            return;
        }

        // 2. 🚀 Načítaj súbor (denník) alebo prebehni celú zložku 'targetPath' a vytiahni matice char[]
        List<LayoutLoader.LayoutInput> loadedLayouts;
        try {
            loadedLayouts = LayoutLoader.loadLayouts(targetPath);
        } catch (IOException e) {
            System.err.println("❌ Chyba pri načítavaní layoutov z disku: " + e.getMessage());
            return;
        }

        if (loadedLayouts.isEmpty()) {
            System.out.println("⚠️ Nenašli sa žiadne platné 30-znakové layouty na analýzu.");
            return;
        }

        // 3. Biomechanické hodnotenie každej jednej načítanej matice (Unikátne menovatele pre každú "inú" klávesnicu)
        List<AnalyzedLayout> leaderboard = new ArrayList<>();
        double englishRatio = 1.0 - slovakRatio;

        for (LayoutLoader.LayoutInput input : loadedLayouts) {
            char[] matrix = input.matrix();

            // Využíva naše preťažené evaluate() - samé si vnútri spočíta presný lokálny základ
            LayoutStats skRes = Evaluator.evaluate(matrix, statsSK, weights);
            LayoutStats enRes = Evaluator.evaluate(matrix, statsEN, weights);

            double combinedScore = (skRes.getScore() * slovakRatio) + (enRes.getScore() * englishRatio);
            leaderboard.add(new AnalyzedLayout(input.name(), matrix, skRes, enRes, combinedScore));
        }

        // 4. Zoradenie rebríčka zostupne podľa bilingválneho Elite skóre
        leaderboard.sort((l1, l2) -> Double.compare(l2.combinedScore, l1.combinedScore));

        // 5. Vykreslenie prehľadnej tabuľky do konzoly
        printLeaderboardTable(leaderboard);

        // 6. 🔍 Hĺbkový röntgen slovníkov pre celú listinu
        System.out.println("\n⏳ Generujem podrobné biomechanické audity do súboru...");
        for (AnalyzedLayout layout : leaderboard) {
            LayoutAnalyzer.AnalysisReport detailedReport = LayoutAnalyzer.generateReport(
                    layout.matrix, layout.name, statsSK, statsEN, dictSK, dictEN, weights, slovakRatio
            );

            try {
                detailedReport.saveToFile("data/best_layout_audit.txt");
            } catch (IOException e) {
                System.err.println("⚠️ Nepodarilo sa uložiť podrobný audit pre " + layout.name + ": " + e.getMessage());
            }
        }
        System.out.println("🎯 Audity boli úspešne vygenerované do: data/best_layout_audit.txt");
    }

    private void printLeaderboardTable(List<AnalyzedLayout> list) {
        System.out.println("\n=======================================================================================================================================================================================");
        System.out.println("                                             VÝSLEDNÁ LISTINA BILINGVÁLNYCH LAYOUTOV (Zoradené podľa: COMBINED ELITE SCORE)");
        System.out.println("=======================================================================================================================================================================================");

        String headerFormat = "%-4s | %-18s | %-7s | %-8s | %-8s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s\n";
        String lineSeparator = "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------";

        System.out.printf(headerFormat, "Idx", "Názov layoutu", "B_ELITE", "SK_SCORE", "EN_SCORE", "SFB%", "SFS%", "ALT%", "ROLLI", "ROLLO", "SCISS", "REDIR", "LSB%", "LHB%", "HOME%", "STRCH");
        System.out.println(lineSeparator);

        int idx = 0;
        for (AnalyzedLayout res : list) {
            // Každých 10 riadkov znova hodíme hlavičku pre lepšiu prehľadnosť v termináli
            if (idx > 0 && idx % 10 == 0) {
                System.out.println(lineSeparator);
                System.out.printf(headerFormat, "Idx", "Názov layoutu", "B_ELITE", "SK_SCORE", "EN_SCORE", "SFB%", "SFS%", "ALT%", "ROLLI", "ROLLO", "SCISS", "REDIR", "LSB%", "LHB%", "HOME%", "STRCH");
                System.out.println(lineSeparator);
            }

            // Metriky zobrazujeme ako vyvážený priemer oboch svetov pre čistý tabuľkový prehľad
            double avgSfb      = (res.skStats.getMetricPercent(MetricType.SFB) + res.enStats.getMetricPercent(MetricType.SFB)) / 2.0;
            double avgSfs      = (res.skStats.getMetricPercent(MetricType.SFS) + res.enStats.getMetricPercent(MetricType.SFS)) / 2.0;
            double avgAlt      = (res.skStats.getMetricPercent(MetricType.ALTERNATION) + res.enStats.getMetricPercent(MetricType.ALTERNATION)) / 2.0;
            double avgRollIn   = (res.skStats.getMetricPercent(MetricType.ROLL_IN) + res.enStats.getMetricPercent(MetricType.ROLL_IN)) / 2.0;
            double avgRollOut  = (res.skStats.getMetricPercent(MetricType.ROLL_OUT) + res.enStats.getMetricPercent(MetricType.ROLL_OUT)) / 2.0;
            double avgScissors = (res.skStats.getMetricPercent(MetricType.SCISSORS) + res.enStats.getMetricPercent(MetricType.SCISSORS)) / 2.0;
            double avgRedirect = (res.skStats.getMetricPercent(MetricType.REDIRECTS) + res.enStats.getMetricPercent(MetricType.REDIRECTS)) / 2.0;
            double avgLsb      = (res.skStats.getMetricPercent(MetricType.LSB) + res.enStats.getMetricPercent(MetricType.LSB)) / 2.0;
            double avgLhb      = (res.skStats.getMetricPercent(MetricType.LHB) + res.enStats.getMetricPercent(MetricType.LHB)) / 2.0;
            double avgHome     = (res.skStats.getMetricPercent(MetricType.HOME_ROW) + res.enStats.getMetricPercent(MetricType.HOME_ROW)) / 2.0;
            double avgStretch  = (res.skStats.getMetricPercent(MetricType.EFFORT) + res.enStats.getMetricPercent(MetricType.EFFORT)) / 2.0;

            String displayName = res.name.length() > 18 ? res.name.substring(0, 15) + "..." : res.name;

            System.out.printf("%-4d | %-18s | %7.4f | %8.4f | %8.4f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f\n",
                    ++idx,
                    displayName,
                    res.combinedScore,
                    res.skStats.getScore(),
                    res.enStats.getScore(),
                    avgSfb,
                    avgSfs,
                    avgAlt,
                    avgRollIn,
                    avgRollOut,
                    avgScissors,
                    avgRedirect,
                    avgLsb,
                    avgLhb,
                    avgHome,
                    avgStretch
            );
        }
        System.out.println("=======================================================================================================================================================================================");
    }
}
