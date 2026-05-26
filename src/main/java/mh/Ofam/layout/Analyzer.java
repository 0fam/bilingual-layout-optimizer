package mh.Ofam.layout;

import lombok.extern.slf4j.Slf4j;
import mh.Ofam.helper.FileSystemHelper;
import mh.optimizer.Weights;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Analyzer {

    public static void main(String[] args) throws URISyntaxException {
        // Cesty môžeme prijímať z argumentov, inak použijeme predvolené
        String korpusPath = "korpus.txt";
        String layoutsDir = "layouts"; // Tvoj adresár s .json súbormi
        boolean optimize = false;

        if (args.length >= 1) korpusPath = args[0];
        if (args.length >= 2) layoutsDir = args[1];

        Map<String, Layout> layoutsToTest = new HashMap<>();
        layoutsToTest = FileSystemHelper.readLayoutsFromDir(layoutsDir);
        RecordParser.loadRecordsToAnalyzer("totem_rekordy.txt", layoutsToTest);

        if (layoutsToTest.isEmpty()) {
            System.err.println("Nenašli sa žiadne platné layouty na testovanie. Končím.");
            return;
        }

        // 2. Načítanie a spracovanie korpusu
        try {
            String rawKorpus = FileSystemHelper.readKorpus(korpusPath);
            if (rawKorpus.strip().isEmpty()) {
                System.err.println("Chyba: Načítaný súbor s korpusom je prázdny!");
                return;
            }
            TextStats textStats = TextStats.instance(rawKorpus);

            if (optimize) {
                Optimizer optimizer = new Optimizer();
                optimizer.optimize(layoutsToTest.get("dof3"), textStats, null);
            } else {

                System.out.println("\nSpúšťam masívnu analýzu pre " + layoutsToTest.size() + " layoutov...\n");

                // List na ukladanie nespracovaných DTO objektov pred ich finálnym zoradením
                List<LayoutStats> allResults = new ArrayList<>();

                // =========================================================================
                //  KONFIGURÁCIA VÁH PRE EXPERIMENTY (Uprav si čísla podľa potreby)
                // =========================================================================
                Weights weights = new Weights();
//            Weights weights = Weights.builder()
//                    // --- KRVAVÁ TROJICA (Toto je fyziológia, tu sa neustupuje) ---
//                    .sfb(1.0)        // TREST: Kmitanie rovnakého prsta (SFB) je smrť.
//                    .stretch(1.0)    // TREST: Ničenie ukazovákov naťahovaním do stredu.
//                    .redirects(1.0)  // TREST: Zmeny smeru a kŕče v ruke (Pinball efekt).
//
//                    // --- SEKUNDÁRNE CHYBY ---
//                    .sfs(1.0)        // TREST: Sendvičové preskakovanie prstov (stále dôležité).
//
//                    // --- MIKRO-BOLESŤ ---
//                    .lsb(1.0)        // TREST: Bočné kríženie.
//                    .lhb(1.0)        // TREST: Ničenie malíčka a prstenníka.
//
//                    // --- UVOĽNENÉ METRIKY (Tu sme znížili váhu) ---
//                    .fingerStats(0.0)  // ZNÍŽENÉ: Dovoľujeme dominantnej ruke prevziať záťaž!
//                    .homeRow(1.0)    // Jemný bonus za stredový riadok.
//                    .scissors(1.0)
//
//                    // --- OSTATNÉ (len pozorujeme) ---
//                    .alt(0.0)
//                    .runs(0.0)
//                    .rollIn(0.0)
//                    .rollOut(0.0)
//
//                    // prsty zatazenie
//                    .pinkyPenalty(1.0) // najtvrdší trest
//                    .ringPenalty(1.0)
//                    .middlePenalty(1.0)
//                    .indexPenalty(1.0) // ukazovák zvládne preťaženie celkom dobre
//                    .build();

                // Spustenie analýzy pre každý načítanud layout
                for (Map.Entry<String, Layout> entry : layoutsToTest.entrySet()) {
                    LayoutStats layoutStats = LayoutStats.instance(textStats.fastCorpus, entry.getValue(), weights, false);
                    allResults.add(layoutStats);
                }

                LeaderboardStats leaderboardStats = LeaderboardStats.instance(weights, allResults, LeaderboardStats.SCORE_METRIC.ELITE_SCORE);
            }
        } catch (IOException e) {
            System.err.println("Chyba pri čítaní korpusu '" + korpusPath + "'. Detaily: " + e.getMessage());
        }
    }

}
