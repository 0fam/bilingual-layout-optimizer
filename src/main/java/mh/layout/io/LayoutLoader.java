package mh.layout.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class LayoutLoader {

    private LayoutLoader() {}

    /**
     * Reprezentuje bezpečne načítaný layout pripravený na analýzu.
     */
    public record LayoutInput(String name, char[] matrix) {}

    /**
     * Načíta 30-znakové layouty z jedného súboru (denníka) alebo z celého priečinka a pridá ich do existujúceho zoznamu.
     */
    public static List<LayoutInput> loadLayouts(String targetPath, List<LayoutInput> results) throws IOException {
        File target = new File(targetPath);

        if (!target.exists()) {
            throw new IOException("Cesta neexistuje: " + targetPath);
        }

        if (target.isDirectory()) {
            // Prípad A: Používateľ poslal cestu k priečinku plnému samostatných layoutov
            File[] files = target.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String content = Files.readString(f.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
                        if (content.length() == 30) {
                            results.add(new LayoutInput(f.getName(), content.toCharArray()));
//                            System.out.println("✅ Načítaný layout z [" + f.getName() + "]");
                        }
                    }
                }
            }
        } else if (target.isFile()) {
            // Prípad B: Používateľ poslal cestu k jednému súboru (napr. layout_journal.txt)
            List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);

            // Sledujeme skutočné číslo riadku v súbore pre dokonalú orientáciu
            int lineNum = 0;

            for (String line : lines) {
                lineNum++;
                if (line.isBlank() || line.startsWith("#")) continue;

                // 🚀 NEPRIESTRELNÝ PARSER S INTELIGENTNÝM MENOVANÍM:

                // 0. Spracovanie formátu logu s "Layout:" a vizuálnymi lomkami (/)
                if (line.contains("Layout:")) {
                    String[] layoutParts = line.split("Layout:", 2);
                    String maybeLayout = layoutParts[1].replace("/", "").replaceAll("\\s+", "");
                    if (maybeLayout.length() == 30) {
                        String iter = extractValue(line, "Iteracia:");
                        // Vygeneruje napr: L1_I203572_L
                        String generatedName = "L" + lineNum + (iter.isEmpty() ? "" : "_I" + iter) + "_L";

                        results.add(new LayoutInput(generatedName, maybeLayout.toCharArray()));
//                        System.out.println("✅ Načítaný layout z logu [" + generatedName + "]");
                        continue;
                    }
                }

                // 1. Ak je to náš žurnál (obsahuje zvislú čiaru '|'), layout je na indexe 0
                if (line.contains("|")) {
                    String[] pipeParts = line.split("\\|");
                    String maybeLayout = pipeParts[0].replaceAll("\\s+", "");
                    if (maybeLayout.length() == 30) {
                        String iter = extractValue(line, "Iterácia:");
                        // Vygeneruje napr: L1_I74498_J
                        String generatedName = "L" + lineNum + (iter.isEmpty() ? "" : "_I" + iter) + "_J";

                        results.add(new LayoutInput(generatedName, maybeLayout.toCharArray()));
//                        System.out.println("✅ Načítaný layout zo journalu [" + generatedName + "]");
                        continue;
                    }
                }

                // 2. Spracovanie formátu kľúč = hodnota (napr. Dvorak = ANAS...)
                if (line.contains(":") || line.contains("=")) {
                    String[] splitParts = line.split("[:=]", 2);
                    String name = splitParts[0].trim();
                    String maybeLayout = splitParts[1].replaceAll("\\s+", "");
                    if (maybeLayout.length() == 30) {
                        results.add(new LayoutInput(name, maybeLayout.toCharArray()));
//                        System.out.println("✅ Načítaný layout z formátu kľúč=hodnota [" + name + "]");
                        continue;
                    }
                }

                // 3. Posledná záchrana: Riadok obsahuje iba čistý 30-znakový layout
                String clean = line.replaceAll("\\s+", "");
                if (clean.length() == 30) {
                    results.add(new LayoutInput("L" + lineNum + "_Pure", clean.toCharArray()));
//                    System.out.println("✅ Načítaný layout z čistého 30-znakového řádku [" + "L" + lineNum + "_Pure" + "]");
                }
            }
        }

        return results;
    }

    /**
     * Pomocný extraktor, ktorý bezpečne vytiahne hodnotu za kľúčom (napr. číslo iterácie)
     */
    private static String extractValue(String line, String key) {
        int idx = line.indexOf(key);
        if (idx == -1) return "";

        int start = idx + key.length();

        // Preskočíme počiatočné medzery za dvojbodkou
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
            start++;
        }

        // Čítame znaky, kým nenazrazíme na koniec, rúru alebo prázdny znak
        int end = start;
        while (end < line.length()) {
            char ch = line.charAt(end);
            if (ch == '|' || Character.isWhitespace(ch)) {
                break;
            }
            end++;
        }

        return line.substring(start, end);
    }
}
