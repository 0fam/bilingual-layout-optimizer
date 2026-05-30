package mh.layout.visualizer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class LayoutVisualizer {

    public static void main(String[] args) {
        String filePath = "c:\\home\\project-personal\\layout-analyzer\\leaderboard.txt";

        Set<String> seenLayouts = new HashSet<>();
        int totalParsed = 0;
        int totalPrinted = 0;

        System.out.println("=== UNIKÁTNY EXPORT: 2vs2 ROVNOVÁHA ALEBO 3+1 ZRKADLOVÝ STĹPEC ===\n");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.contains("|") || line.contains("Názov layoutu") || line.contains("VÝSLEDNÁ")) {
                    continue;
                }

                String[] parts = line.split("\\|");
                if (parts.length < 5) continue;

                String index = parts[0].trim();
                String name = parts[1].trim();
                String row1 = parts[2].trim();
                String row2 = parts[3].trim();
                String row3 = parts[4].trim();

                if (row1.isEmpty() || row2.isEmpty() || row3.isEmpty()) continue;
                totalParsed++;

                String uniqueKey = row1 + "|" + row2 + "|" + row3;
                if (seenLayouts.contains(uniqueKey)) {
                    continue;
                }

                // -----------------------------------------------------------------
                // NOVÝ FLEXIBILNÝ FILTER: Povolí 2vs2 ALEBO tvoj 3+1 zrkadlový stĺpec
                // -----------------------------------------------------------------
                if (!isValidKognitiveTopology(row1, row2, row3)) {
                    continue;
                }

                seenLayouts.add(uniqueKey);
                totalPrinted++;

                System.out.println("# Pozícia " + totalPrinted + " (Pôvodné Idx " + index + "): " + name);
                printFormattedRow(row1);
                printFormattedRow(row2);
                printFormattedRow(row3);
                System.out.println();
            }

            System.out.println("---------------------------------------------------");
            System.out.printf("📊 Štatistika: Preverených %d riadkov | Po uvoľnení filtra na 3+1 zostalo %d unikátnych layoutov.%n",
                    totalParsed, totalPrinted);

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní súboru: " + e.getMessage());
        }
    }

    /**
     * Overuje topológiu symbolov podľa dvoch kognitívnych pravidiel:
     * 1. Presná rovnováha rúk 2 vs 2.
     * 2. Usporiadaný stĺpcový zrkadlový zhluk 3 + 1 (trojka kompletne nad sebou v jednom stĺpci).
     */
    private static boolean isValidKognitiveTopology(String r1, String r2, String r3) {
        String[] p1 = r1.split("\\s+");
        String[] p2 = r2.split("\\s+");
        String[] p3 = r3.split("\\s+");

        if (p1.length != 2 || p2.length != 2 || p3.length != 2) return false;

        // Pole pre spočítanie symbolov v každom z 10 stĺpcov klávesnice
        int[] colCounts = new int[10];

        // Zrekonštruujeme celé 10-znakové riadky matice
        String[] rows = { p1[0] + p1[1], p2[0] + p2[1], p3[0] + p3[1] };

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 10; c++) {
                char ch = rows[r].charAt(c);
                if (ch == ',' || ch == '.' || ch == '-' || ch == '\'') {
                    colCounts[c]++;
                }
            }
        }

        // --- PRAVIDLO 1: Klasická rovnováha 2 naľavo vs 2 napravo ---
        int leftTotal = colCounts[0] + colCounts[1] + colCounts[2] + colCounts[3] + colCounts[4];
        int rightTotal = colCounts[5] + colCounts[6] + colCounts[7] + colCounts[8] + colCounts[9];
        if (leftTotal == 2 && rightTotal == 2) {
            return true;
        }

        // --- PRAVIDLO 2: Tvoj nový 3+1 stĺpcový vzorec (Vertikálny Monolit) ---
        // Prechádzame zrkadlové dvojičky stĺpcov: (0,9), (1,8), (2,7), (3,6), (4,5)
        for (int c = 0; c < 5; c++) {
            int mirrorCol = 9 - c;
            int countLeft = colCounts[c];
            int countRight = colCounts[mirrorCol];

            // Ak je v jednom stĺpci trojka kompletne nad sebou a v jeho zrkadlovom náprotivku presne jeden symbol
            if ((countLeft == 3 && countRight == 1) || (countLeft == 1 && countRight == 3)) {
                return true;
            }
        }

        return false;
    }

    private static void printFormattedRow(String rawRow) {
        String[] hands = rawRow.split("\\s+");
        if (hands.length != 2) return;
        System.out.println(formatWithSpaces(hands[0]) + "   " + formatWithSpaces(hands[1]));
    }

    private static String formatWithSpaces(String hand) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hand.length(); i++) {
            sb.append(hand.charAt(i));
            if (i < hand.length() - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
