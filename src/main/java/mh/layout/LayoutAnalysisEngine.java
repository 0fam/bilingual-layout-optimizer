package mh.layout;

import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.io.CorpusProcessor;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.MetricType;
import mh.layout.stats.NGramStats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class LayoutAnalysisEngine {
    private final Weights weights = new Weights();
    private final NGramStats statsSK = new NGramStats();
    private final NGramStats statsEN = new NGramStats();

    private Map<String, Double> dictSK;
    private Map<String, Double> dictEN;

    private final double slovakRatio = 0.60;

    // Pomocný kontajner pre zoradenie výsledkov v leaderboarde
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

        // 1. Inicializuj ergonomické váhy -> Už sa stalo automaticky vo fieldoch triedy (new Weights())

        // 2. Volaj CorpusProcessor na načítanie invariantných štatistík textu (SK a EN)
        System.out.println("📥 Načítavam jazykové korpusy a slovníky...");
        try {
            this.dictSK = CorpusProcessor.processFile("data/slovak_corpus.txt", statsSK);
            System.out.println("✅ Slovenský korpus úspešne načítaný.");

            this.dictEN = CorpusProcessor.processFile("data/english_corpus.txt", statsEN);
            System.out.println("✅ Anglický korpus úspešne načítaný.\n");
        } catch (IOException e) {
            System.err.println("❌ Chyba pri načítavaní korpusov: " + e.getMessage());
            return;
        }

        // 3. Načítaj súbor alebo prebehni zložku 'targetPath' a vytiahni z nej matice char[]
        List<Map.Entry<String, char[]>> loadedLayouts = new ArrayList<>();
        File target = new File(targetPath);

        try {
            if (target.isDirectory()) {
                // Prebehni zložku súbor po súbore (napr. čo súbor, to jeden layout)
                File[] files = target.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
                            if (content.length() == 30) {
                                loadedLayouts.add(Map.entry(f.getName(), content.toCharArray()));
                            }
                        }
                    }
                }
            } else if (target.isFile()) {
                // Načítaj jeden súbor (napr. layout_journal.txt) riadok po riadku
                List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
                int counter = 1;
                for (String line : lines) {
                    if (line.isBlank() || line.startsWith("#")) continue;

                    String clean = line.replaceAll("\\s+", "");
                    if (clean.length() == 30) {
                        loadedLayouts.add(Map.entry("Journal_Layout_" + counter++, clean.toCharArray()));
                    } else if (line.contains(":") || line.contains("=")) {
                        String[] parts = line.split("[:=]");
                        if (parts.length == 2 && parts[1].trim().length() == 30) {
                            loadedLayouts.add(Map.entry(parts[0].trim(), parts[1].trim().toCharArray()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Chyba pri čítaní cieľovej cesty layoutov: " + e.getMessage());
            return;
        }

        if (loadedLayouts.isEmpty()) {
            System.out.println("⚠️ Nenašli sa žiadne platné 30-znakové layouty na analýzu.");
            return;
        }

        // 4. Každú maticu prežeň cez Evaluator.evaluate(...)
        // 5. Spáruj názov súboru/layoutu s jeho kombinovaným skóre
        List<AnalyzedLayout> leaderboard = new ArrayList<>();
        double englishRatio = 1.0 - slovakRatio;

        for (Map.Entry<String, char[]> entry : loadedLayouts) {
            char[] matrix = entry.getValue();

            LayoutStats skRes = Evaluator.evaluate(matrix, statsSK, weights);
            LayoutStats enRes = Evaluator.evaluate(matrix, statsEN, weights);

            // Výpočet kombinovaného bilingválneho Elite skóre
            double combinedScore = (skRes.getScore() * slovakRatio) + (enRes.getScore() * englishRatio);

            leaderboard.add(new AnalyzedLayout(entry.getKey(), matrix, skRes, enRes, combinedScore));
        }

        // 6. Zorď výsledky zostupne (od najvyššieho skóre po najnižšie)
        leaderboard.sort((l1, l2) -> Double.compare(l2.combinedScore, l1.combinedScore));

        // 7. Vytlač do konzoly nádherný bilingválny LEADERBOARD
        printLeaderboardTable(leaderboard);
    }

    private void printLeaderboardTable(List<AnalyzedLayout> list) {
        System.out.println("\n=======================================================================================================================================================================================");
        System.out.println("                                             VÝSLEDNÁ LISTINA BILINGVÁLNYCH LAYOUTOV (Zoradené podľa: COMBINED ELITE SCORE)");
        System.out.println("=======================================================================================================================================================================================");

        // Formát tabuľky postavený výhradne na našich reálnych biomechanických metrikách
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

            // Metriky zobrazujeme ako bilingválny priemer (SK + EN) pre dokonalý jednoradový prehľad
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
