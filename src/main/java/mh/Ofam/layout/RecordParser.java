package mh.Ofam.layout;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

public class RecordParser {
    public static void loadRecordsToAnalyzer(String filePath, Map<String, Layout> layoutsToTest) {
        try {
            int lineNum = 0;
            for (String line : Files.readAllLines(Paths.get(filePath))) {
                lineNum++;
                if (!line.contains("Layout:")) continue;

                String flat = line.split("Layout:")[1].replace(" ", "").replace("/", "").trim();
                String name = "line_" + lineNum;

                // Kontrola dĺžky – ak zlyhá, vieš presne, ktorý riadok opraviť
                if (flat.length() == 30) {
                    layoutsToTest.put(name, new Layout(name, flat.toCharArray()));
                } else {
                    System.err.println("⚠️ Chyba parsovania (riadok " + lineNum + "): Matica má dĺžku " + flat.length() + " namiesto 30! [" + flat + "]");
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Kritická chyba: Nepodarilo sa načítať súbor '" + filePath + "' -> " + e.getMessage());
        }
    }

}
