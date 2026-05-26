package mh.Ofam.layout;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

public class RecordParser {
    public static void loadRecordsToAnalyzer(String filePath, Map<String, Layout> layoutsToTest) {
        try {
            int lineNum = 0; // Súbor čítame odhora, pre ľudí číslujeme od 1
            for (String line : Files.readAllLines(Paths.get(filePath))) {
                lineNum++;
                if (!line.contains("Layout:")) continue;

                // 1. Vyčistenie a získanie 30-písmenovej matice
                String flat = line.split("Layout:")[1].replace(" ", "").replace("/", "").trim();

                if (flat.length() != 30) {
                    System.err.println("⚠️ Chyba parsovania (riadok " + lineNum + "): Dĺžka " + flat.length() + " namiesto 30!");
                    continue;
                }

                // 2. Extrakcia Iterácie a Skóre priamo z logu pre unikátne meno
                String iter = "0";
                String score = "00.0000";
                try {
                    if (line.contains("Iteracia:") && line.contains("SKORE:")) {
                        iter = line.split("Iteracia:")[1].split("\\|")[0].trim();
                        score = line.split("SKORE:")[1].split("\\|")[0].trim();
                    }
                } catch (Exception e) {
                    // Ak by sa formát logu jemne sekol, prežijeme to s predvolenými hodnotami
                }

                // Vytvoríme dokonale identifikovateľný kľúč, napr: "L43_Iter583_Skore89.8152"
                String uniqueName = String.format("L%d_Iter%s_Skore%s", lineNum, iter, score);
                layoutsToTest.put(uniqueName, new Layout(uniqueName, flat.toCharArray()));
            }
        } catch (IOException e) {
            System.err.println("❌ Kritická chyba: Nepodarilo sa načítať súbor '" + filePath + "' -> " + e.getMessage());
        }
    }

}
