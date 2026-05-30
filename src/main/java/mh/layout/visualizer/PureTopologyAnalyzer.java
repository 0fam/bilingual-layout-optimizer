package mh.layout.visualizer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PureTopologyAnalyzer {

    public static void main(String[] args) {
        String filePath = "c:\\home\\project-personal\\layout-analyzer\\leaderboard.txt";

        // Mapa pre uchovanie čisto geometrických matíc a počtu ich výskytov
        Map<String, Integer> topologyMap = new LinkedHashMap<>();
        int totalLayoutsParsed = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Filtrujeme iba riadky s dátami tabuľky
                if (!line.contains("|") || line.contains("Názov layoutu") || line.contains("VÝSLEDNÁ")) {
                    continue;
                }

                String[] parts = line.split("\\|");
                if (parts.length < 5) continue;

                String r1 = parts[2].trim();
                String r2 = parts[3].trim();
                String r3 = parts[4].trim();

                if (r1.isEmpty() || r2.isEmpty() || r3.isEmpty()) continue;
                totalLayoutsParsed++;

                // Úplná maska: Písmená -> '_', Akýkoľvek symbol -> 'X'
                String mask1 = maskRowToPureX(r1);
                String mask2 = maskRowToPureX(r2);
                String mask3 = maskRowToPureX(r3);

                if (mask1 == null || mask2 == null || mask3 == null) continue;

                // Vytvoríme unikátny geometrický kľúč
                String topologyKey = mask1 + "\n" + mask2 + "\n" + mask3;

                // Sčítavame výskyty rovnakej slepej topológie
                topologyMap.put(topologyKey, topologyMap.getOrDefault(topologyKey, 0) + 1);
            }

            // =========================================================================
            // ČISTÝ GEOMETRICKÝ VÝSTUP
            // =========================================================================
            System.out.println("===================================================");
            System.out.println("   PURE GEOMETRIC TOPOLOGY (MATICE ROZLOŽENIA)    ");
            System.out.println("===================================================\n");

            int counter = 1;
            for (Map.Entry<String, Integer> entry : topologyMap.entrySet()) {
                System.out.printf("# Topológia %d (Objavila sa v logu: %dx)%n", counter, entry.getValue());
                System.out.println(entry.getKey());
                System.out.println();
                counter++;
            }

            System.out.println("---------------------------------------------------");
            System.out.printf("📊 Štatistika: Spracovaných %d riadkov žurnálu.%n", totalLayoutsParsed);
            System.out.printf("👑 POČET UNIKÁTNYCH GEOMETRICKÝCH VZORCOV: %d%n", topologyMap.size());
            System.out.println("---------------------------------------------------");

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní súboru: " + e.getMessage());
        }
    }

    private static String maskRowToPureX(String rawRow) {
        String[] hands = rawRow.split("\\s+");
        if (hands.length != 2) return null;

        return maskHandToPureX(hands[0]) + "   " + maskHandToPureX(hands[1]);
    }

    /**
     * Vymaže identitu všetkého. Písmená sú '_', symboly sú nekompromisne 'X'.
     */
    private static String maskHandToPureX(String hand) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hand.length(); i++) {
            char c = hand.charAt(i);

            // Ak je to akýkoľvek z našich 4 symbolov, nahradíme ho iba čistým X
            if (c < 'A' || c > 'Z') {
//            if (c == ',' || c == '.' || c == '-' || c == '\'') {
                sb.append('X');
            } else {
                sb.append('_');
            }

            if (i < hand.length() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}
