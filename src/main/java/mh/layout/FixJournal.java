package mh.layout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class FixJournal {
    public static void main(String[] args) {
        String fileName = "c:\\home\\project-personal\\layout-analyzer\\layout_journal.txt";

        System.out.println("⏳ Štartujem bleskovú opravu 35MB žurnálu...");
        try {
            // Načítame celý súbor do pamäte ako jeden String
            String content = Files.readString(Paths.get(fileName));

            // Spustíme ten istý regex, ale na úrovni procesora bez GUI balastu
            String fixedContent = content.replaceAll(
                    "(.*\\(Jadro \\d+\\))\\r?\\n\\s*\\|\\s*(\\d+)",
                    "$1 | Iterácia: $2\n"
            );

            // Zapíšeme opravený výsledok späť do toho istého súboru
            Files.writeString(Paths.get(fileName), fixedContent);

            System.out.println("✅ HOTOVO! Žurnál bol úspešne uprataný a zarovnaný.");
            System.out.println("📊 Teraz môžeš bezpečne spustiť LayoutAnalysisEngine.");

        } catch (IOException e) {
            System.err.println("❌ Chyba pri práci so súborom: " + e.getMessage());
        }
    }
}