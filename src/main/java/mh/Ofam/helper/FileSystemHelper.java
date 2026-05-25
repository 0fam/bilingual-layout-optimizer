package mh.Ofam.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mh.Ofam.layout.Analyzer;
import mh.Ofam.layout.Layout;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class FileSystemHelper {
    public static Path getResourcePathForDir(String dir) throws URISyntaxException {
        return Path.of(Analyzer.class.getResource("/" + dir).toURI());
    }

    // Zoberie cestu k adresáru a načíta všetky .json súbory
    public static Map<String, Layout> loadLayoutsFromDirectory(Path dir) {
        Map<String, Layout> layouts = new HashMap<>();

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
                    layouts.put(layout.getName(), layout);
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

    public static Map<String, Layout> readLayoutsFromDir(String layoutsDir) throws URISyntaxException {
        // 1. Načítanie všetkých layoutov zo zložky
        Path laoutsDirPath = FileSystemHelper.getResourcePathForDir(layoutsDir);
        System.out.println("\nHľadám .json layouty v adresári: " + laoutsDirPath.toAbsolutePath());
        Map<String, Layout> layoutsToTest = FileSystemHelper.loadLayoutsFromDirectory(laoutsDirPath);
        return layoutsToTest;
    }

    public static String readKorpus(String korpusPath) throws URISyntaxException, IOException {
        Path cestaKSuboru = FileSystemHelper.getResourcePathForDir(korpusPath);
        System.out.println("\nNačítavam korpus z: " + cestaKSuboru.toAbsolutePath());
        String rawKorpus = Files.readString(cestaKSuboru);
        return rawKorpus;
    }

}
