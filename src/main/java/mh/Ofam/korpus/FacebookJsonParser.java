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

public class FacebookJsonParser {
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    public static void main(String[] args) {
        // PREPÍNAČE KONFIGURÁCIE (Ako v Gemini)
        boolean zobratIbaPrvyRiadokOtazky = true; // ak je true, tvoje správy oseká po prvý enter
        boolean generovatOdpovede = false;        // ak je false, súbor s odpoveďami (_A) sa nevytvorí
        String targetSender = "Marek Hudek";      // tvoje meno pre filter otázok

        // VSTUPY A VÝSTUPY (Presne podľa teba)
        String directory = "C:/home/project-personal/korpus/fb/";
        String fileName = "message_2";
        String fileType = ".json";
        Path jsonPath = Paths.get(directory + fileName + fileType);

        Path outputPathOtazky = Paths.get(directory + fileName + "_Q" + (zobratIbaPrvyRiadokOtazky ? "1" : "") + ".txt");
        Path outputPathOdpovede = Paths.get(directory + fileName + "_A" + ".txt");

        System.out.println("Spracovávam Facebook JSON a rozdeľujem podľa tvojho footprintu...");

        long spracovanychSprav = 0;
        long riadkovSpracovanych = 0;
        String pamatanySender = null;

        try (BufferedReader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8);
             BufferedWriter writerOtazky = Files.newBufferedWriter(outputPathOtazky, StandardCharsets.UTF_8);
             BufferedWriter writerOdpovede = generovatOdpovede ? Files.newBufferedWriter(outputPathOdpovede, StandardCharsets.UTF_8) : null) {

            String line;
            while ((line = reader.readLine()) != null) {
                riadkovSpracovanych++;

                // 1. KROK: Odložíme si meno odosielateľa
                if (line.contains("\"sender_name\"")) {
                    pamatanySender = extractValueSimple(line);
                }
                // 2. KROK: Narazili sme na obsah správy
                else if (line.contains("\"content\"")) {
                    String value = extractValueSimple(line);

                    if (value != null && pamatanySender != null) {
                        // Dekódovanie Unicode a oprava Facebook Mojibake bugu
                        String deuncoded = decodeJsonAndUnicode(value);
                        String cistyText = repairMojibake(deuncoded);

                        // FILTER FB BALASTU: Ignorujeme systémové hlášky o fotkách a prílohách
                        if (cistyText.contains("poslal prílohu") || cistyText.contains("poslala prílohu") ||
                                cistyText.contains("poslal fotku") || cistyText.contains("poslala fotku")) {
                            pamatanySender = null;
                            continue;
                        }

                        // Ak správu písal Marek Hudek -> ide do OTÁZOK (_Q / _Q1)
                        if (targetSender.equals(pamatanySender)) {
                            if (zobratIbaPrvyRiadokOtazky) {
                                int enterIdx = cistyText.indexOf("\n");
                                if (enterIdx != -1) {
                                    cistyText = cistyText.substring(0, enterIdx);
                                }
                            }
                            writerOtazky.write(cistyText);
                            writerOtazky.newLine();
                        }
                        // Ak správu písal niekto iný -> ide do ODPOVEDÍ (_A)
                        else {
                            if (writerOdpovede != null) {
                                writerOdpovede.write(cistyText);
                                writerOdpovede.newLine();
                            }
                        }

                        spracovanychSprav++;
                    }
                    pamatanySender = null; // Reset pre ďalší blok
                }
            }

            System.out.println("=================================================");
            System.out.println("  BINGO! Facebook korpus je úspešne na disku.   ");
            System.out.println("=================================================");
            System.out.printf(" Spracovaných riadkov celkom: %d\n", riadkovSpracovanych);
            System.out.printf(" Vytiahnutých čistých správ:  %d\n", spracovanychSprav);
            System.out.printf(" Surové Otázky (Ty):  %s\n", outputPathOtazky.toAbsolutePath());
            if (generovatOdpovede) {
                System.out.printf(" Surové Odpovede (Oni): %s\n", outputPathOdpovede.toAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní/zápise: " + e.getMessage());
        }
    }

    private static String extractValueSimple(String line) {
        int colonIdx = line.indexOf(":");
        if (colonIdx == -1) return null;

        int startQuote = line.indexOf("\"", colonIdx);
        if (startQuote == -1) return null;

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

    private static String repairMojibake(String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return text;
        }
    }
}
