package mh.layout.optimizer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LayoutJournal {

    public static final String JOURNAL_FILE = "c:\\home\\project-personal\\bilingual-layout-optimizer\\layout_journal.txt";
    private static final Object journalLock = new Object();

    public static void append(char[] matrix, double sk, double en, double combined, int threadId, long iteration) {
        synchronized (journalLock) {
            try (FileWriter fw = new FileWriter(JOURNAL_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.printf("%s | SK: %.4f | EN: %.4f | COMBINED: %.4f | %s (Core %d) | Iteration: %d%n",
                        format(matrix), sk, en, combined, timestamp, threadId, iteration);

            } catch (IOException e) {
                System.err.println("Chyba zápisu do denníka: " + e.getMessage());
            }
        }
    }

    public static String format(char[] matrix) {
        if (matrix == null || matrix.length != 30) {
            return "";
        }

        StringBuilder sb = new StringBuilder(37); // Presná dĺžka stringu vrátane medzier

        // Riadok 1 (0-4 | medzera | 5-9) + 2 medzery na oddelenie riadkov
        sb.append(matrix, 0, 5).append(' ').append(matrix, 5, 5).append("  ");

        // Riadok 2 (10-14 | medzera | 15-19) + 2 medzery na oddelenie riadkov
        sb.append(matrix, 10, 5).append(' ').append(matrix, 15, 5).append("  ");

        // Riadok 3 (20-24 | medzera | 25-29)
        sb.append(matrix, 20, 5).append(' ').append(matrix, 25, 5);

        return sb.toString();
    }

}
