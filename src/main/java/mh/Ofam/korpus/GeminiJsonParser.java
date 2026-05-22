package mh.Ofam.korpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiJsonParser {
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    public static void main(String[] args) {
        // VSTUP: Tvoj surový JSON
        String directory = "C:/home/project-personal/korpus/gemini/";
        String fileName = "mh";
        String fileType = ".json";
        Path jsonPath = Paths.get(directory + fileName + fileType);

        // VÝSTUPY: Dva samostatné surové korpusy
        Path outputPathOtazky = Paths.get(directory + fileName + "_Q" + ".txt");
        Path outputPathOdpovede = Paths.get(directory + fileName + "_A" + ".txt");

        System.out.println("Spracovávam JSON a rozdeľujem otázky a odpovede...");

        long spracovanychParov = 0;
        long riadkovSpracovanych = 0;
        String pamatanaOtazka = null; // Stavová pamäť pre otázku

        try (BufferedReader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8);
             BufferedWriter writerOtazky = Files.newBufferedWriter(outputPathOtazky, StandardCharsets.UTF_8);
             BufferedWriter writerOdpovede = Paths.get(outputPathOdpovede.toUri()) != null ? Files.newBufferedWriter(outputPathOdpovede, StandardCharsets.UTF_8) : null) {

            String line;
            while ((line = reader.readLine()) != null) {
                riadkovSpracovanych++;

                // 1. KROK: Vytiahnutie a odloženie tvojej otázky
                if (line.contains("\"title\"")) {
                    String value = extractValueSimple(line);
                    if (value != null && (value.startsWith("Prompted ") || value.startsWith("Zadaný pokyn:"))) {
                        pamatanaOtazka = value.replaceAll("^(?:Prompted|Zadaný pokyn:)\\s*", "");
                    }
                }
                // 2. KROK: Vytiahnutie odpovede a okamžitý separátny zápis
                else if (line.contains("\"html\"") && pamatanaOtazka != null) {
                    String value = extractValueSimple(line);
                    if (value != null) {
                        // Dekódovanie JSON a Unicode (\\u00xx) escape znakov
                        String cistaOtazka = decodeJsonAndUnicode(pamatanaOtazka);
                        String cistaOdpoved = decodeJsonAndUnicode(value);

                        // Odstránenie HTML balastu z odpovede
                        cistaOdpoved = cistaOdpoved.replaceAll("<[^>]+>", " ");

                        // Zápis čistého textu priamo do príslušných súborov (bez prefixov a oddeľovačov)
                        writerOtazky.write(cistaOtazka);
                        writerOtazky.newLine();

                        if (writerOdpovede != null) {
                            writerOdpovede.write(cistaOdpoved);
                            writerOdpovede.newLine();
                        }

                        spracovanychParov++;
                        pamatanaOtazka = null; // Reset stavu pre ďalšiu dvojicu
                    }
                }
            }

            System.out.println("=================================================");
            System.out.println("  HURÁ! Čisté korpusy sú úspešne na disku. ");
            System.out.println("=================================================");
            System.out.printf(" Spracovaných riadkov celkom: %d\n", riadkovSpracovanych);
            System.out.printf(" Vytiahnutých čistých dvojíc: %d\n", spracovanychParov);
            System.out.printf(" Surové Otázky:  %s\n", outputPathOtazky.toAbsolutePath());
            System.out.printf(" Surové Odpovede: %s\n", outputPathOdpovede.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Chyba pri prúdovom zápise: " + e.getMessage());
        }
    }

    // Úplne primitívne a nepriestrelné vytiahnutie textu z úvodzoviek na riadku
    private static String extractValueSimple(String line) {
        int colonIdx = line.indexOf(":");
        if (colonIdx == -1) return null;

        // Nájdeme prvú úvodzovku ZA dvojbodkou
        int startQuote = line.indexOf("\"", colonIdx);
        if (startQuote == -1) return null;

        // Nájdeme úplne poslednú úvodzovku na celom riadku
        int endQuote = line.lastIndexOf("\"");
        if (endQuote <= startQuote) return null;

        return line.substring(startQuote + 1, endQuote);
    }

    private static String decodeJsonAndUnicode(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher m = UNICODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char ch = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
