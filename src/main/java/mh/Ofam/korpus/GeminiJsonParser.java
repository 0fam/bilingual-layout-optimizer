package mh.Ofam.korpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiJsonParser {
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}");
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    public static void main(String[] args) {
        // VSTUP: Tvoj surový JSON
        Path jsonPath = Paths.get("C:/home/project-personal/korpus/gemini/hh.json");

        // VÝSTUPY: Dva hľadané súbory (Otázka + Odpoveď v oboch)
        Path outputPathDiacritics = Paths.get("C:/home/project-personal/korpus/gemini/hh_diakritika.txt");
        Path outputPathFlat = Paths.get("C:/home/project-personal/korpus/gemini/hh_flat.txt");

        System.out.println("Spúšťam jednoduchý riadkový parser...");

        long spracovanychParov = 0;
        long riadkovSpracovanych = 0;
        String pamatanaOtazka = null; // Stavová pamäť pre otázku

        try (BufferedReader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8);
             BufferedWriter writerDiac = Files.newBufferedWriter(outputPathDiacritics, StandardCharsets.UTF_8);
             BufferedWriter writerFlat = Files.newBufferedWriter(outputPathFlat, StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                riadkovSpracovanych++;

                // 1. KROK: Riadok obsahuje otázku
                if (line.contains("\"title\"")) {
                    String value = extractValueSimple(line);
                    if (value != null && (value.startsWith("Prompted ") || value.startsWith("Zadaný pokyn:"))) {
                        // Uložíme do pamäte a odrežeme prefix
                        pamatanaOtazka = value.replaceAll("^(?:Prompted|Zadaný pokyn:)\\s*", "");
                    }
                }
                // 2. KROK: Riadok obsahuje odpoveď a v pamäti máme odloženú otázku
                else if (line.contains("\"html\"") && pamatanaOtazka != null) {
                    String value = extractValueSimple(line);
                    if (value != null) {
                        // Dekódovanie JSON a Unicode escape sekvencií
                        String cistaOtazka = decodeJsonAndUnicode(pamatanaOtazka);
                        String cistaOdpoved = decodeJsonAndUnicode(value);

                        // Vyčistenie HTML tagov
                        cistaOdpoved = cistaOdpoved.replaceAll("<[^>]+>", " ");

                        // ZÁPIS 1: Verzia s diakritikou (pre testovanie vrstiev)
                        writerDiac.write("OTÁZKA:\n" + cistaOtazka + "\n\nODPOVEĎ:\n" + cistaOdpoved + "\n");
                        writerDiac.write("--------------------------------------------------\n");

                        // ZÁPIS 2: Verzia FLAT (malé písmená, bez diakritiky)
                        String flatOtazka = stripDiacritics(cistaOtazka).toLowerCase();
                        String flatOdpoved = stripDiacritics(cistaOdpoved).toLowerCase();
                        writerFlat.write("otazka:\n" + flatOtazka + "\n\nodpoved:\n" + flatOdpoved + "\n");
                        writerFlat.write("--------------------------------------------------\n");

                        spracovanychParov++;
                        pamatanaOtazka = null; // Reset stavu pre ďalšiu dvojicu
                    }
                }
            }

            System.out.println("=================================================");
            System.out.println("  HURÁ! Parser úspešne dobehol. ");
            System.out.println("=================================================");
            System.out.printf(" Spracovaných riadkov celkom: %d\n", riadkovSpracovanych);
            System.out.printf(" Spárovaných Q&A dialógov:    %d\n", spracovanychParov);

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní/zápise: " + e.getMessage());
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

    private static String stripDiacritics(String src) {
        if (src == null) return "";
        String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }
}
