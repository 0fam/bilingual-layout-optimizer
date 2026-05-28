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
     * Načíta 30-znakové layouty z jedného súboru (denníka) alebo z celého priečinka.
     */
    public static List<LayoutInput> loadLayouts(String targetPath) throws IOException {
        List<LayoutInput> result = new ArrayList<>();
        File target = new File(targetPath);

        if (!target.exists()) {
            throw new IOException("Cesta neexistuje: " + targetPath);
        }

        if (target.isDirectory()) {
            // Prípad A: Používateľ poslal priečinok (napr. JSONy alebo textáky od kamošov)
            File[] files = target.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String content = Files.readString(f.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
                        if (content.length() == 30) {
                            result.add(new LayoutInput(f.getName(), content.toCharArray()));
                        }
                    }
                }
            }
        } else if (target.isFile()) {
            // Prípad B: Čítame náš vygenerovaný žurnál riadok po riadku
            List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
            int counter = 1;

            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;

                // 1. Spracovanie línie zo žurnálu (oddelenej zvislou čiarou '|')
                if (line.contains("|")) {
                    String[] pipeParts = line.split("\\|");
                    String maybeLayout = pipeParts[0].replaceAll("\\s+", "");
                    if (maybeLayout.length() == 30) {
                        result.add(new LayoutInput("Journal_Layout_" + counter++, maybeLayout.toCharArray()));
                        continue;
                    }
                }

                // 2. Spracovanie formátu kľúč = hodnota (napr. Dvorak = ANAS...)
                if (line.contains(":") || line.contains("=")) {
                    String[] splitParts = line.split("[:=]", 2);
                    String name = splitParts[0].trim();
                    String maybeLayout = splitParts[1].replaceAll("\\s+", "");
                    if (maybeLayout.length() == 30) {
                        result.add(new LayoutInput(name, maybeLayout.toCharArray()));
                        continue;
                    }
                }

                // 3. Posledná záchrana: Riadok obsahuje iba čistý 30-znakový layout
                String clean = line.replaceAll("\\s+", "");
                if (clean.length() == 30) {
                    result.add(new LayoutInput("Layout_" + counter++, clean.toCharArray()));
                }
            }
        }

        return result;
    }
}
