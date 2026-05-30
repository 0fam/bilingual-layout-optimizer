package mh.layout;

import mh.layout.analyzer.AnalyzedLayout;
import mh.layout.analyzer.LayoutAnalyzer;
import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.io.LayoutLoader;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.MetricType;
import mh.layout.stats.NGramStats;
import mh.layout.topology.Finger;

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

    private final double slovakRatio = 0.80;

    // 🚀 REFAKTOR: Žiadne načítavanie, všetko prichádza hotové na zlatom podnose
    public void runStandalone(NGramStats statsSK, NGramStats statsEN,
                              Map<String, Double> dictSK, Map<String, Double> dictEN,
                              List<String> targetPaths,
                              String leaderBoardFile, String analysesFile) {
        System.out.println("========================================================================================");
        System.out.println("               SPÚŠŤAM STANDALONE BIOMECHANICKÚ ANALÝZU LAYOUTOV                        ");
        System.out.println("========================================================================================\n");

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

        // 🏁 ŠTART STOPIEK (v nanosekundách)
        long startTime = System.nanoTime();
        long counter = 0;
        for (LayoutLoader.LayoutInput input : loadedLayouts) {
            char[] matrix = input.matrix();

            // Využíva naše preťažené evaluate() - samé si vnútri spočíta presný lokálny základ
            LayoutStats skRes = Evaluator.evaluate(matrix, statsSK, weights);

            // 🚀 FIX: Ak sme v single režime, evaluate pre EN preskočíme a priradíme referenciu. Šetríme 50% CPU!
            LayoutStats enRes = statsEN == null ? null : Evaluator.evaluate(matrix, statsEN, weights);

            leaderboard.add(AnalyzedLayout.instance(input.name(), matrix, skRes, enRes, slovakRatio));

//            System.out.printf("✅ Spracované: %-18s | Elite Score: %.4f%n", input.name(), combinedScore);
            // cislo nasobok 2 na X - 1 potrebujes 0000111111 seriu jednotiek na konci
            if ((counter++ & 0x3FF) == 0) {
                System.out.print("\r Analyzujem layout " + counter + " " + input.name() + "          ");
            }
        }
        System.out.print("\r                                                "); // vymaz stary zaznam
        System.out.print("\r"); // zresetuj uroven kurzora

        // ⏱️ STOP STOPIEK
        long endTime = System.nanoTime();

        // Výpočet čistého času v nanosekundách
        long durationInNanoseconds = endTime - startTime;
        // Prepočet na sekundy (double kvôli desatinným miestam)
        double totalSeconds = durationInNanoseconds / 1_000_000_000.0;
        // 🏎️ VÝKONNOSTNÝ UKAZOVATEĽ: Koľko layoutov prefrčalo engineom za jednu sekundu
        double layoutsPerSecond = (totalSeconds > 0) ? (counter / totalSeconds) : 0;
        // Zobrazenie výsledkov do konzoly
        System.out.println("\n======================================================================");
        System.out.println("                     🚀 VÝKONNOSTNÝ AUDIT ENGINU                      ");
        System.out.println("======================================================================");
        System.out.printf("⏱️ Celkový čas behu:       %,.2f sekúnd (cca %,.1f min)%n", totalSeconds, (totalSeconds / 60.0));
        System.out.printf("📊 Celkovo overených:      %,d layoutov%n", counter);
        System.out.printf("🏎️ Rýchlosť spracovania:   💥 %,.0f layoutov / sekundu 💥%n", layoutsPerSecond);
        System.out.println("======================================================================");

        // 4. Zoradenie rebríčka zostupne podľa bilingválneho Elite skóre
        leaderboard.sort((l1, l2) -> Double.compare(l2.combinedScore(), l1.combinedScore()));

        // 🚀 EXPORT: Zapíše usporiadanú tabuľku výhradne na disk (Konzolu už neotravuje)
        saveSortedLeaderboardToFile(leaderboard, leaderBoardFile);

        // nechceme detailnu analyzu, napriklad kvoli velkemu poctu layoutov, staci iba leaderboard bez detailov
        if (analysesFile == null) return;

        // 🔍 Hĺbkový röntgen layoutov
        int counter2 = 0;
        int max = 4;
        System.out.println("\n⏳ Generujem podrobné biomechanické audity do súboru...");
        for (AnalyzedLayout layout : leaderboard) {
            if (counter2++ > max) {
                System.out.println("⚠️ Generujem maximalne " + max + " layoutov");
                break;
            }
            // 🚀 FIX: Ak bežíme jeden jazyk, pošleme do reportu rovnaké SK/EN sady a váhu 100% pre SK, nech report nehalucinuje
            LayoutAnalyzer.AnalysisReport detailedReport = LayoutAnalyzer.generateReport(
                    layout, dictSK, dictEN, slovakRatio
            );

            try {
                detailedReport.saveToFile(analysesFile);
            } catch (IOException e) {
                System.err.println("⚠️ Nepodarilo sa uložiť podrobný audit pre " + layout.name() + ": " + e.getMessage());
            }
        }
        System.out.println("🎯 Audity layoutov boli úspešne vygenerované do: " + analysesFile);
    }

    private double combineScore(double sk, double en, double ratioSk) {
        // 🚀 Gilotína: Ak je angličtina čisté utrpenie, layout okamžite diskvalifikujeme
        if (en < 75.0 || sk < 50.0) {
            return 10.0; // vyhodiť z rebríčka
        } else {
//            return (sk * ratioSk) + (en * (1 - ratioSk));
            return Math.pow(sk, ratioSk) * Math.pow(en, 1 - ratioSk);
        }
    }

    /**
     * 💾 Uloží kompletne zoradený rebríček do textového súboru. Konzola zostáva čistá.
     */
    private void saveSortedLeaderboardToFile(List<AnalyzedLayout> sortedList, String outputFilePath) {
        if (sortedList.isEmpty()) return;
        File dir = new File("data");
        if (!dir.exists()) dir.mkdirs();

        boolean bi = sortedList.getFirst().enStats() != null;

        try (PrintWriter fileOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), StandardCharsets.UTF_8))) {

            // Zápis hlavičky výhradne do súboru
            fileOut.println("=================================================================================================================================================================================================================================================");
            fileOut.println("                                                                             VÝSLEDNÁ LISTINA LAYOUTOV (Zoradené podľa: (COMBINED) SCORE)");
            fileOut.println("=================================================================================================================================================================================================================================================");

            String colHeadFormat = bi ? "| %-11s " : "| %-5s ";

            // 🚀 DYNAMICKÝ REFAKTOR: Šírka stĺpca presne kopíruje dĺžku nášho nového vizuálu prstov (63 znakov pre bilingvál, 28 pre single)
            String fingHeadFormat = bi ? "| %-63s " : "| %-28s ";

            // 🚀 FIX: fingHeadFormat je tu už iba JEDENKRÁT (odstránený chybný .repeat(2))
            String headerFormat = "%-4s | %-18s | %-41s | %-7s | %-8s | %-8s " + colHeadFormat.repeat(11) + fingHeadFormat + "%n";
            String lineSeparator = "-".repeat(bi ? 257 : 195) + "\n"; // Automatická dĺžka oddeľovača

            String fingHead = bi
                    ? " LP LR LM LI . RI RM RR RP [SK][EN] LP LR LM LI . RI RM RR RP"
                    : " LP LR LM LI . RI RM RR RP";

            fileOut.printf(headerFormat, "Idx", "Názov layoutu", "VIZUÁLNY LAYOUT KLÁVESNICE", "B_ELITE", "SK_SCORE", "EN_SCORE", "SFB%", "SFS%", "ALT%", "ROLLI", "ROLLO", "SCISS", "REDIR", "LSB%", "LHB%", "HOME%", "STRCH", fingHead);
            fileOut.print(lineSeparator);

            int idx = 0;
            for (AnalyzedLayout res : sortedList) {
                // Každých 10 riadkov hodíme pod-hlavičku kvôli scannovaniu v textovom editore
                if (idx > 0 && idx % 10 == 0) {
                    fileOut.print(lineSeparator);
                    fileOut.printf(headerFormat, "Idx", "Názov layoutu", "VIZUÁLNY LAYOUT KLÁVESNICE", "B_ELITE", "SK_SCORE", "EN_SCORE", "SFB%", "SFS%", "ALT%", "ROLLI", "ROLLO", "SCISS", "REDIR", "LSB%", "LHB%", "HOME%", "STRCH", fingHead);
                    fileOut.print(lineSeparator);
                }

                double[] sfb      = {res.skStats().getMetricPercent(MetricType.SFB), bi ? res.enStats().getMetricPercent(MetricType.SFB) : 0.0};
                double[] sfs      = {res.skStats().getMetricPercent(MetricType.SFS), bi ? res.enStats().getMetricPercent(MetricType.SFS) : 0.0};
                double[] alt      = {res.skStats().getMetricPercent(MetricType.ALTERNATION), bi ? res.enStats().getMetricPercent(MetricType.ALTERNATION) : 0.0};
                double[] rollIn   = {res.skStats().getMetricPercent(MetricType.ROLL_IN), bi ? res.enStats().getMetricPercent(MetricType.ROLL_IN) : 0.0};
                double[] rollOut  = {res.skStats().getMetricPercent(MetricType.ROLL_OUT), bi ? res.enStats().getMetricPercent(MetricType.ROLL_OUT) : 0.0};
                double[] scissors = {res.skStats().getMetricPercent(MetricType.SCISSORS), bi ? res.enStats().getMetricPercent(MetricType.SCISSORS) : 0.0};
                double[] redirect = {res.skStats().getMetricPercent(MetricType.REDIRECTS), bi ? res.enStats().getMetricPercent(MetricType.REDIRECTS) : 0.0};
                double[] lsb      = {res.skStats().getMetricPercent(MetricType.LSB), bi ? res.enStats().getMetricPercent(MetricType.LSB) : 0.0};
                double[] lhb      = {res.skStats().getMetricPercent(MetricType.LHB), bi ? res.enStats().getMetricPercent(MetricType.LHB) : 0.0};
                double[] home     = {res.skStats().getMetricPercent(MetricType.HOME_ROW), bi ? res.enStats().getMetricPercent(MetricType.HOME_ROW) : 0.0};
                double[] stretch  = {res.skStats().getMetricPercent(MetricType.EFFORT), bi ? res.enStats().getMetricPercent(MetricType.EFFORT) : 0.0};

                // 🚀 VÝPOČET ZÁŤAŽE PRSTOV (Formát: LP LR LM LI • RI RM RR RP)
                String fingerStr;

                if (bi) {
                    // 🌍 BILINGVÁLNY VÝPIS
                    fingerStr = String.format(" %02.0f %02.0f %02.0f %02.0f • %02.0f %02.0f %02.0f %02.0f   %02.0f %02.0f %02.0f %02.0f • %02.0f %02.0f %02.0f %02.0f",
                            res.skStats().getFingerPercent(Finger.LP.ordinal()), res.skStats().getFingerPercent(Finger.LR.ordinal()),
                            res.skStats().getFingerPercent(Finger.LM.ordinal()), res.skStats().getFingerPercent(Finger.LI.ordinal()),
                            res.skStats().getFingerPercent(Finger.RI.ordinal()), res.skStats().getFingerPercent(Finger.RM.ordinal()),
                            res.skStats().getFingerPercent(Finger.RR.ordinal()), res.skStats().getFingerPercent(Finger.RP.ordinal()),
                            res.enStats().getFingerPercent(Finger.LP.ordinal()), res.enStats().getFingerPercent(Finger.LR.ordinal()),
                            res.enStats().getFingerPercent(Finger.LM.ordinal()), res.enStats().getFingerPercent(Finger.LI.ordinal()),
                            res.enStats().getFingerPercent(Finger.RI.ordinal()), res.enStats().getFingerPercent(Finger.RM.ordinal()),
                            res.enStats().getFingerPercent(Finger.RR.ordinal()), res.enStats().getFingerPercent(Finger.RP.ordinal()));
                } else {
                    // 🇸🇰 MONOLINGVÁLNY VÝPIS: Pridané 3 medzery na začiatok kvôli zarovnaniu pod "SK LP..."
                    fingerStr = String.format(" %02.0f %02.0f %02.0f %02.0f • %02.0f %02.0f %02.0f %02.0f",
                            res.skStats().getFingerPercent(Finger.LP.ordinal()), res.skStats().getFingerPercent(Finger.LR.ordinal()),
                            res.skStats().getFingerPercent(Finger.LM.ordinal()), res.skStats().getFingerPercent(Finger.LI.ordinal()),
                            res.skStats().getFingerPercent(Finger.RI.ordinal()), res.skStats().getFingerPercent(Finger.RM.ordinal()),
                            res.skStats().getFingerPercent(Finger.RR.ordinal()), res.skStats().getFingerPercent(Finger.RP.ordinal()));
                }

                String displayName = res.name().length() > 18 ? res.name().substring(0, 15) + "..." : res.name();
                // 🚀 Prevedieme 30 znakovú maticu na čitateľnú mapu prstov a rúk
                String visualLayout = formatMatrixVisual(res.matrix());

                String col = bi ? "| %5.2f %5.2f ": "| %5.2f ";

                // 🚀 FIX: lineFormat na konci upravený na čistý fingHeadFormat bez .repeat(2)
                String lineFormat = "%-4d | %-18s | %-41s | %7.4f | %8.4f | %8.4f " + col.repeat(11) + fingHeadFormat + "%n";

                if (!bi) {
                    fileOut.printf(lineFormat,
                            ++idx, displayName, visualLayout, res.combinedScore(), res.skStats().getScore(), 0.0,
                            sfb[0], sfs[0], alt[0], rollIn[0], rollOut[0], scissors[0], redirect[0], lsb[0], lhb[0], home[0], stretch[0],
                            fingerStr
                    );
                } else {
                    fileOut.printf(lineFormat,
                            ++idx, displayName, visualLayout, res.combinedScore(), res.skStats().getScore(), res.enStats().getScore(),
                            sfb[0], sfb[1], sfs[0], sfs[1], alt[0], alt[1], rollIn[0], rollIn[1], rollOut[0], rollOut[1], scissors[0], scissors[1],
                            redirect[0], redirect[1], lsb[0], lsb[1], lhb[0], lhb[1], home[0], home[1], stretch[0], stretch[1],
                            fingerStr
                    );
                }
            }

            fileOut.println("=================================================================================================================================================================================================================================================");
            // Jediný oznam, ktorý pošleme do konzoly
            System.out.println("\n💾 REBRÍČEK ULOŽENÝ: Usporiadaná výsledná listina vrátane záťaže prstov je na disku: " + outputFilePath);

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
