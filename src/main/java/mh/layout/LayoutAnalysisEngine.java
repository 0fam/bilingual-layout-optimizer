package mh.layout;

import mh.layout.analyzer.LayoutAnalyzer;
import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.io.CorpusProcessor;
import mh.layout.io.LayoutLoader;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.MetricType;
import mh.layout.stats.NGramStats;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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

    public void runStandalone(List<String> targetPaths, String skKorpus, String enKorpus, String leaderBoardFile, String analysesFile) {
        System.out.println("========================================================================================");
        System.out.println("               SPÚŠŤAM STANDALONE BIOMECHANICKÚ ANALÝZU LAYOUTOV                        ");
        System.out.println("========================================================================================\n");

        // 🚀 FIX: Detekcia režimu pre jeden jazyk
        boolean singleLang = enKorpus == null || enKorpus.isBlank() || enKorpus.equals(skKorpus);

        // 1. Načítanie invariantných štatistík textu (Slovníky pre röntgen prstov)
        System.out.println("📥 Načítavam jazykové korpusy a slovníky slov...");
        try {
            this.dictSK = CorpusProcessor.processFile(skKorpus, statsSK);
            System.out.println("✅ Hlavný korpus úspešne spracovaný.");

            // 🚀 FIX: Druhý korpus načítame len vtedy, ak nie sme v režime jedného jazyka
            if (!singleLang) {
                this.dictEN = CorpusProcessor.processFile(enKorpus, statsEN);
                System.out.println("✅ Anglický korpus úspešne spracovaný.\n");
            } else {
                System.out.println("ℹ️ Detegovaný režim pre jeden jazyk. Druhé načítanie preskočené.\n");
            }
        } catch (IOException e) {
            System.err.println("❌ KRITICKÁ CHYBA: Nepodarilo sa načítať korpusy: " + e.getMessage());
            return;
        }

        // 2. 🚀 AGREGÁCIA Z VIACERÝCH CIEST: Postupne plníme spoločný Master List
        List<LayoutLoader.LayoutInput> loadedLayouts = new ArrayList<>();

        for (String path : targetPaths) {
            try {
                // 🚀 Načítaj súbor (denník) alebo prebehni celú zložku 'targetPath' a vytiahni matice char[]
                LayoutLoader.loadLayouts(path, loadedLayouts);
            } catch (IOException e) {
                System.err.println("❌ Chyba pri načítavaní layoutov z cesty [" + path + "]: " + e.getMessage());
                // Pokračujeme na ďalšiu cestu, nevzdávame to kvôli jednému chybnému súboru
            }
        }

        if (loadedLayouts.isEmpty()) {
            System.out.println("⚠️ Nenašli sa žiadne platné 30-znakové layouty na analýzu.");
            return;
        }

        System.out.printf("📦 Celkovo načítaných %d layoutov. Spúšťam analýzu...%n%n", loadedLayouts.size());

        // 3. Biomechanické hodnotenie každej jednej načítanej matice (Unikátne menovatele pre každú "inú" klávesnicu)
        List<AnalyzedLayout> leaderboard = new ArrayList<>();
        double englishRatio = 1.0 - slovakRatio;

        long counter = 0;
        for (LayoutLoader.LayoutInput input : loadedLayouts) {
            char[] matrix = input.matrix();

            // Využíva naše preťažené evaluate() - samé si vnútri spočíta presný lokálny základ
            LayoutStats skRes = Evaluator.evaluate(matrix, statsSK, weights);

            // 🚀 FIX: Ak sme v single režime, evaluate pre EN preskočíme a priradíme referenciu. Šetríme 50% CPU!
            LayoutStats enRes = singleLang ? skRes : Evaluator.evaluate(matrix, statsEN, weights);

            double combinedScore = singleLang ? skRes.getScore() : (skRes.getScore() * slovakRatio) + (enRes.getScore() * englishRatio);
            leaderboard.add(new AnalyzedLayout(input.name(), matrix, skRes, enRes, combinedScore));

//            System.out.printf("✅ Spracované: %-18s | Elite Score: %.4f%n", input.name(), combinedScore);
            // cislo nasobok 2 na X - 1 potrebujes 0000111111 seriu jednotiek na konci
            if ((counter++ & 63) == 0) {
                System.out.print("\r Analyzujem layout " + counter + " " + input.name() + "          ");
            }
        }

        // 4. Zoradenie rebríčka zostupne podľa bilingválneho Elite skóre
        leaderboard.sort((l1, l2) -> Double.compare(l2.combinedScore, l1.combinedScore));

        // 🚀 EXPORT: Zapíše usporiadanú tabuľku výhradne na disk (Konzolu už neotravuje)
        saveSortedLeaderboardToFile(leaderboard, leaderBoardFile);

        // nechceme detailnu analyzu, napriklad kvoli velkemu poctu layoutov, staci iba leaderboard bez detailov
        if (analysesFile == null) return;

        // 🔍 Hĺbkový röntgen layoutov
        System.out.println("\n⏳ Generujem podrobné biomechanické audity do súboru...");
        for (AnalyzedLayout layout : leaderboard) {
            // 🚀 FIX: Ak bežíme jeden jazyk, pošleme do reportu rovnaké SK/EN sady a váhu 100% pre SK, nech report nehalucinuje
            LayoutAnalyzer.AnalysisReport detailedReport = LayoutAnalyzer.generateReport(
                    layout.matrix, layout.name, statsSK, singleLang ? statsSK : statsEN, dictSK, singleLang ? dictSK : dictEN, weights, singleLang ? 1.0 : slovakRatio
            );

            try {
                detailedReport.saveToFile(analysesFile);
            } catch (IOException e) {
                System.err.println("⚠️ Nepodarilo sa uložiť podrobný audit pre " + layout.name + ": " + e.getMessage());
            }
        }
        System.out.println("🎯 Audity layoutov boli úspešne vygenerované do: " + analysesFile);
    }

    /**
     * 💾 Uloží kompletne zoradený rebríček do textového súboru. Konzola zostáva čistá.
     */
    private void saveSortedLeaderboardToFile(List<AnalyzedLayout> sortedList, String outputFilePath) {
        File dir = new File("data");
        if (!dir.exists()) dir.mkdirs();

        try (PrintWriter fileOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), StandardCharsets.UTF_8))) {

            // Zápis hlavičky výhradne do súboru
            fileOut.println("===================================================================================================================================================================================================================");
            fileOut.println("                                                              VÝSLEDNÁ LISTINA BILINGVÁLNYCH LAYOUTOV (Zoradené podľa: COMBINED ELITE SCORE)");
            fileOut.println("===================================================================================================================================================================================================================");

            // 🚀 ROZŠÍRENIE: Pridaný formát %-41s pre stĺpec vizuálneho layoutu klávesnice
            String headerFormat = "%-4s | %-18s | %-41s | %-7s | %-8s | %-8s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s%n";
            String lineSeparator = "-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n";

            fileOut.printf(headerFormat, "Idx", "Názov layoutu", "VIZUÁLNY LAYOUT KLÁVESNICE", "B_ELITE", "SK_SCORE", "EN_SCORE", "SFB%", "SFS%", "ALT%", "ROLLI", "ROLLO", "SCISS", "REDIR", "LSB%", "LHB%", "HOME%", "STRCH");
            fileOut.print(lineSeparator);

            int idx = 0;
            for (AnalyzedLayout res : sortedList) {
                // Každých 10 riadkov hodíme pod-hlavičku kvôli scannovaniu v textovom editore
                if (idx > 0 && idx % 10 == 0) {
                    fileOut.print(lineSeparator);
                    fileOut.printf(headerFormat, "Idx", "Názov layoutu", "VIZUÁLNY LAYOUT KLÁVESNICE", "B_ELITE", "SK_SCORE", "EN_SCORE", "SFB%", "SFS%", "ALT%", "ROLLI", "ROLLO", "SCISS", "REDIR", "LSB%", "LHB%", "HOME%", "STRCH");
                    fileOut.print(lineSeparator);
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

                // 🚀 MINIMÁLNA ZMENA: Prevedieme 30 znakovú maticu na čitateľnú mapu prstov a rúk
                String visualLayout = formatMatrixVisual(res.matrix);

                fileOut.printf("%-4d | %-18s | %-41s | %7.4f | %8.4f | %8.4f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f | %5.2f%n",
                        ++idx, displayName, visualLayout, res.combinedScore, res.skStats.getScore(), res.enStats.getScore(),
                        avgSfb, avgSfs, avgAlt, avgRollIn, avgRollOut, avgScissors, avgRedirect, avgLsb, avgLhb, avgHome, avgStretch
                );
            }

            fileOut.println("===================================================================================================================================================================================================================");

            // Jediný oznam, ktorý pošleme do konzoly
            System.out.println("\n💾 REBRÍČEK ULOŽENÝ: Usporiadaná výsledná listina je na disku: " + outputFilePath);

        } catch (IOException e) {
            System.err.println("⚠️ Chyba pri zápise rebríčka do súboru: " + e.getMessage());
        }
    }

    /**
     * 🚀 POMOCNÁ METÓDA: Transformuje 30-znakový jednorozmerný char[] na vizuálnu trojriadkovú mriežku klávesnice.
     */
    private String formatMatrixVisual(char[] m) {
        if (m == null || m.length != 30) return "";
        return String.format("%s %s | %s %s | %s %s",
                new String(m, 0, 5),  new String(m, 5, 5),
                new String(m, 10, 5), new String(m, 15, 5),
                new String(m, 20, 5), new String(m, 25, 5)
        );
    }
}
