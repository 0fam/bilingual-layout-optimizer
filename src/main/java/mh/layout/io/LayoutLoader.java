package mh.layout.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class LayoutLoader {

    private LayoutLoader() {}

    /**
     * Reprezentuje bezpečne načítaný layout pripravený na analýzu.
     */
    public record LayoutInput(String name, char[] matrix) {}

    /**
     * Načíta 30-znakové layouty z jedného súboru (denníka) alebo z celého priečinka a pridá ich do existujúceho zoznamu.
     * Názvy layoutov zo samostatnych suborov automaticky prefixuje prvými dvoma znakmi z názvu zdrojového súboru.
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
                    try {
                        if (f.isFile()) {
                            if (f.getName().endsWith(".dof")) {
                                String dofMatrix = parseDofMatrix(f);
                                if (dofMatrix != null) {
                                    results.add(new LayoutInput(f.getName(), dofMatrix.toCharArray()));
                                }
                            } else {
                                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
                                content = content.trim();
                                if (content.length() == 30) {
                                    // 🚀 FIX: Prevod na UpperCase pre priečinok samostatných súborov
                                    results.add(new LayoutInput(f.getName(), content.toUpperCase().toCharArray()));
                                }
                            }
                        }
                    } catch (IOException e) {
                        System.err.println();
                        System.err.println("❌ Chyba pri načítavaní layoutu [" + f.getName() + "]: " + e.getMessage());
                        // Pokračujeme na ďalšiu cestu, nevzdávame to kvôli jednému chybnému súboru
                    }
                }
            }
        } else if (target.isFile()) {
            // Prípad B: Používateľ poslal cestu k jednému súboru (napr. layout_journal.txt)
            // 🚀 BEZPEČNÝ PREFIX PRE SAMOSTATNÝ SÚBOR (napr. "01_")
            String fileName = target.getName();
            String prefix = fileName.substring(0, Math.min(fileName.length(), 2)).toUpperCase() + "_";

            if (fileName.endsWith(".dof")) {
                String dofMatrix = parseDofMatrix(target);
                if (dofMatrix != null) {
                    results.add(new LayoutInput(fileName, dofMatrix.toCharArray()));
                }
            } else {
                List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);

                // Sledujeme skutočné číslo riadku v súbore pre dokonalú orientáciu
                int lineNum = 0;

                for (String line : lines) {
                    lineNum++;
                    if (line.isBlank() || line.startsWith("#")) continue;

                    // 🚀 NEPRIESTRELNÝ PARSER S INTELIGENTNÝM MENOVANÍM A FIXOM VEĽKOSTI PÍSMEN:

                    // 0. Spracovanie nového formátu logu s "Layout:" a vizuálnymi lomkami (/)
                    if (line.contains("Layout:")) {
                        String[] layoutParts = line.split("Layout:", 2);
                        String maybeLayout = layoutParts[1].replace("/", "").replaceAll("\\s+", "");
                        if (maybeLayout.length() == 30) {
                            String iter = extractValue(line, "Iteracia:");
                            // 🎯 Zakomponovaný prefix (napr. SK_L1_I203572_L)
//                            String generatedName = prefix + "L" + lineNum + (iter.isEmpty() ? "" : "_I" + iter) + "_L";
                            String generatedName = prefix + "L" + lineNum + "_L";

                            results.add(new LayoutInput(generatedName, maybeLayout.toUpperCase().toCharArray()));
                            continue;
                        }
                    }

                    // 1. Ak je to náš žurnál (obsahuje zvislú čiaru '|'), layout je na indexe 0
                    if (line.contains("|")) {
                        String[] pipeParts = line.split("\\|");
                        String maybeLayout = pipeParts[0].replaceAll("\\s+", "");
                        if (maybeLayout.length() == 30) {
                            String iter = extractValue(line, "Iterácia:");
                            // 🎯 Zakomponovaný prefix (napr. SK_L1_I74498_J)
//                            String generatedName = prefix + "L" + lineNum + (iter.isEmpty() ? "" : "_I" + iter) + "_J";
                            String generatedName = prefix + "L" + lineNum + "_J";

                            results.add(new LayoutInput(generatedName, maybeLayout.toUpperCase().toCharArray()));
                            continue;
                        }
                    }

                    // 2. Spracovanie formátu kľúč = hodnota (napr. Dvorak = ANAS...)
                    if (line.contains(":") || line.contains("=")) {
                        String[] splitParts = line.split("[:=]", 2);
                        String name = splitParts[0].trim();
                        String maybeLayout = splitParts[1].replaceAll("\\s+", "");
                        if (maybeLayout.length() == 30) {
                            // 🎯 Zakomponovaný prefix pred pôvodný názov (napr. SK_Dvorak)
                            results.add(new LayoutInput(prefix + name, maybeLayout.toUpperCase().toCharArray()));
                            continue;
                        }
                    }

                    // 3. Posledná záchrana: Riadok obsahuje iba čistý 30-znakový layout
                    String clean = line.replaceAll("\\s+", "");
                    if (clean.length() == 30) {
                        // 🎯 Zakomponovaný prefix (napr. SK_L1_Pure)
                        results.add(new LayoutInput(prefix + "L" + lineNum + "_Pure", clean.toUpperCase().toCharArray()));
                    }
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

    /**
     * Samostatný bezpečný parser pre súbory *.dof bez externých knižníc
     */
    private static String parseDofMatrix(File file) {
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            int mainStart = json.indexOf("\"main\":");
            if (mainStart != -1) {
                int startBracket = json.indexOf("[", mainStart);
                int endBracket = json.indexOf("]", startBracket);
                if (startBracket != -1 && endBracket != -1) {
                    String mainContent = json.substring(startBracket + 1, endBracket);
                    StringBuilder sb = new StringBuilder();
                    for (String line : mainContent.split("\n")) {
                        int firstQuote = line.indexOf("\"");
                        int lastQuote = line.lastIndexOf("\"");
                        if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
                            sb.append(line.substring(firstQuote + 1, lastQuote).replaceAll("\\s+", ""));
                        }
                    }
                    if (sb.length() == 30) {
                        return sb.toString().toUpperCase();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ Chyba pri čítaní .dof súboru: " + e.getMessage());
        }
        return null;
    }
}
