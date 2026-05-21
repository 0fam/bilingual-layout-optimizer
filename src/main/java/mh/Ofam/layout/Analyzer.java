package mh.Ofam.layout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mh.Ofam.helper.FileSystemHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class Analyzer {

    // Zoberie cestu k adresáru a načíta všetky .json súbory
    public static Map<String, Layout> loadLayoutsFromDirectory(Path dir) {
        Map<String, Layout> layouts = new LinkedHashMap<>();

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("UPOZORNENIE: Adresár s layoutmi '" + dir.toAbsolutePath() + "' neexistuje.");
            return layouts;
        }

        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.dof")) {
            for (Path entry : stream) {
//                System.out.print("  Čítam súbor " + entry.getFileName());
                String content = Files.readString(entry);
                Layout layout = parseJsonToLayout(content);
                if (layout != null) {
                    layouts.put(layout.name, layout);
//                    System.out.println("  ✓ Načítaný layout: " + layout.name + " zo súboru " + entry.getFileName());
                } else {
                    System.err.println("  ❌ Chyba parsovania v súbore: " + entry.getFileName());
                }
            }
        } catch (IOException e) {
            System.err.println("Chyba pri čítaní adresára: " + e.getMessage());
        }
        return layouts;
    }

    // --- NOVÝ TOKENS-SAFE OBJEKTOVÝ PARSER S JACKSONOM ---
    private static Layout parseJsonToLayout(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            String name = root.has("name") ? root.get("name").asText() : "Unknown";

            if (root.has("layers") && root.get("layers").has("main")) {
                JsonNode mainBlock = root.get("layers").get("main");

                if (mainBlock.size() >= 3) {
                    String[] cleanRows = new String[3];
                    for (int i = 0; i < 3; i++) {
                        // Čistá extrakcia stringu bez ohľadu na prítomnosť špeciálnych znakov
                        String rawRow = mainBlock.get(i).asText();
                        cleanRows[i] = rawRow.replaceAll("\\s+", "");
                    }
                    return new Layout(name, cleanRows);
                } else {
                    System.err.println("  ❌ Malo riadko v layoute");
                }
            } else {
                System.err.println("  ❌ Prazdny layout");
            }
        } catch (Exception e) {
            System.err.println("  ❌ Chyba Jackson objektového parsovania: " + e.getMessage());
        }
        return null;
    }

    // Bez-knižnicový regex parser pre špecifický formát klávesníc
    private static Layout parseJsonToLayout2(String json) {
        String name = "Unknown";

        // 1. Vytiahnutie mena layoutu
        java.util.regex.Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (nameMatcher.find()) {
            name = nameMatcher.group(1);
        }

        // 2. Vytiahnutie bloku "main": [ ... ]
        java.util.regex.Matcher mainMatcher = Pattern.compile("\"main\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (mainMatcher.find()) {
            String mainBlock = mainMatcher.group(1);

            // 3. Vytiahnutie jednotlivých riadkov s úvodzovkami
            java.util.regex.Matcher rowMatcher = Pattern.compile("\"([^\"]+)\"").matcher(mainBlock);
            List<String> rawRows = new ArrayList<>();
            while (rowMatcher.find()) {
                rawRows.add(rowMatcher.group(1));
            }

            // 4. Konverzia na čisté 10-znakové stringy (odstránenie bielych znakov)
            if (rawRows.size() >= 3) {
                String[] cleanRows = new String[3];
                for (int i = 0; i < 3; i++) {
                    // Prevedie "q  g  d  f..." na čisté "qgdf..."
                    cleanRows[i] = rawRows.get(i).replaceAll("\\s+", "");
                }
                return new Layout(name, cleanRows);
            }
        }
        return null;
    }
    
    public static void main(String[] args) throws URISyntaxException {
        // Cesty môžeme prijímať z argumentov, inak použijeme predvolené
        String korpusPath = "korpus.txt";
        String layoutsDir = "layouts"; // Tvoj adresár s .json súbormi

        if (args.length >= 1) korpusPath = args[0];
        if (args.length >= 2) layoutsDir = args[1];

        // 1. Načítanie všetkých layoutov zo zložky
        Path laoutsDirPath = FileSystemHelper.getResourcePathForDir(layoutsDir);
        log.info("\nHľadám .json layouty v adresári: {}", laoutsDirPath.toAbsolutePath());
        Map<String, Layout> layoutsToTest = loadLayoutsFromDirectory(laoutsDirPath);

        if (layoutsToTest.isEmpty()) {
            System.err.println("Nenašli sa žiadne platné layouty na testovanie. Končím.");
            return;
        }

        // 2. Načítanie a spracovanie korpusu
        try {
            Path cestaKSuboru = FileSystemHelper.getResourcePathForDir(korpusPath);
            System.out.println("\nNačítavam korpus z: " + cestaKSuboru.toAbsolutePath());
            String rawKorpus = Files.readString(cestaKSuboru);

            if (rawKorpus.strip().isEmpty()) {
                System.err.println("Chyba: Načítaný súbor s korpusom je prázdny!");
                return;
            }
            TextStats textStats = TextStats.instance(rawKorpus);

            System.out.println("\nSpúšťam masívnu analýzu pre " + layoutsToTest.size() + " layoutov...\n");

            // List na ukladanie nespracovaných DTO objektov pred ich finálnym zoradením
            List<AnalysisResult> allResults = new ArrayList<>();

            // Spustenie analýzy pre každý načítanud layout
            for (Map.Entry<String, Layout> entry : layoutsToTest.entrySet()) {
                LayoutStats layoutStats = LayoutStats.instance(textStats, entry.getValue());
                allResults.add(new AnalysisResult(entry.getKey(), layoutStats));
            }

            // =========================================================================
            //  KONFIGURÁCIA VÁH PRE EXPERIMENTY (Uprav si čísla podľa potreby)
            // =========================================================================
            Weights weights = Weights.builder().sfb(1.0).sfs(3.0).stretch(5.0).alt(0.0).runs(0.0)
                    .scissors(0.0).redirects(5.0).rollIn(0.0).rollOut(0.0)
                    .build();

// --- ZABIJACI (Hardcore tresty) ---
//            weights.put("SFB", 5.0);        // Absolútny kráľ bolesti. Kmitanie jedným prstom netolerujeme.
//            weights.put("SCISSORS", 3.0);   // Kríženie prstov medzi riadkami je ergonomické peklo.
//            weights.put("REDIRECTS", 3.0);  // Zvýšená váha z 2.0 na 3.0! Keďže už neriešime "pekné" rolly, musíme o to tvrdšie trestať "pinball" (sekanie smeru), inak nám ruka na jednej strane umrie.
//            weights.put("STRETCH", 2.0);    // Strážca Totemu. Ukazováky zostanú doma.
//            weights.put("SFS", 1.0);        // Skipgramy nechávame ako jemný filter proti sendvičovému kmitaniu.
//
//// --- VYPNUTÝ ŠUM (Všetko na nulu) ---
//            weights.put("ROLL_IN", 0.0);
//            weights.put("ROLL_OUT", 0.0);
//            weights.put("ALT", 0.0);
//            weights.put("RUNS", 0.0);

            // --- PROFIL: PURE FLOW & TENDON HEALTH ---
//            weights.put("SFB", 5.0);
//            weights.put("SCISSORS", 3.0);
//            weights.put("STRETCH", 2.0);
//            weights.put("REDIRECTS", 2.0);
//            weights.put("SFS", 1.0);
//
//            // Odmeny musia mať kladné váhy, pretože logika odmeňovania je už preklopená pri výpočte skóre
//            weights.put("ROLL_IN", 1.0);
//            weights.put("ROLL_OUT", 0.5);
//
//            // Ignorujeme alternáciu a valivé behy trestáme iba jemne
//            weights.put("ALT", 0.0);
//            weights.put("RUNS", 0.5);
            
            LeaderboardStats leaderboardStats = LeaderboardStats.instance(weights, allResults);

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní korpusu '" + korpusPath + "'. Detaily: " + e.getMessage());
        }
    }

}
