package mh.layout.visualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TemplateTester {

    public static void main(String[] args) {
        List<int[]> validTemplates = generateValidTemplates();

        System.out.println("====================================================");
        System.out.println("👑 DIGNOSNÝ VÝPIS KANONICKÝCH GEOMETRIÍ SYMBOLOV");
        System.out.println("====================================================");
        System.out.println("Celkový počet unikátnych šablón po zrkadlení: " + validTemplates.size());
        System.out.println("----------------------------------------------------\n");

        // Vykreslíme prvých 15 šablón na vizuálnu kontrolu (môžeš zmeniť na validTemplates.size())
        int limit = Math.min(150, validTemplates.size());
        for (int i = 0; i < limit; i++) {
            System.out.println("📐 ŠABLÓNA CHALAN v poradí #" + (i + 1) + " (Indexy: " + Arrays.toString(validTemplates.get(i)) + ")");
            drawKeyboardMatrix(validTemplates.get(i));
            System.out.println("----------------------------------------------------");
        }
    }

    public static List<int[]> generateValidTemplates() {
        List<int[]> allTemplates = new ArrayList<>();
        // 30 nad 4 kombinatorický priestor
        for (int i = 0; i < 27; i++) {
            for (int j = i + 1; j < 28; j++) {
                for (int k = j + 1; k < 29; k++) {
                    for (int l = k + 1; l < 30; l++) {
                        int[] template = {i, j, k, l};
                        if (isGeometryHumanFriendly(template)) {
                            allTemplates.add(template);
                        }
                    }
                }
            }
        }

        // Zrkadlový redukčný filter (Odstraňuje pravoruké duplicity jednorukých/stretch vzorov)
        List<int[]> canonicalTemplates = new ArrayList<>();
        for (int[] t : allTemplates) {
            int[] mirrorT = new int[4];
            for (int i = 0; i < 4; i++) {
                int row = t[i] / 10;
                int col = t[i] % 10;
                mirrorT[i] = row * 10 + (9 - col);
            }
            Arrays.sort(mirrorT);

            boolean hasMirror = false;
            for (int[] existing : canonicalTemplates) {
                if (Arrays.equals(existing, mirrorT)) {
                    hasMirror = true;
                    break;
                }
            }

            if (!hasMirror || Arrays.equals(t, mirrorT)) {
                canonicalTemplates.add(t);
            }
        }
        return canonicalTemplates;
    }

    private static boolean isGeometryHumanFriendly(int[] idx) {
        // 🛑 ABSOLÚTNY ZÁKON HOME ROW: Na riadku 1 smú stáť symboly IBA na stredových stretch indexoch 14 a 15
        for (int i = 0; i < 4; i++) {
            int pos = idx[i];
            int row = pos / 10;
            if (row == 1 && pos != 14 && pos != 15) {
                return false;
            }
        }

        int[] r = new int[4];
        int[] c = new int[4];
        int countCol4 = 0;
        int countCol5 = 0;

        for (int i = 0; i < 4; i++) {
            r[i] = idx[i] / 10;
            c[i] = idx[i] % 10;
            if (c[i] == 4) countCol4++;
            if (c[i] == 5) countCol5++;
        }

        // VZOREC 5: 3+1 Centrálny Stretch Pilier
        if ((countCol4 == 3 && countCol5 == 1) || (countCol4 == 1 && countCol5 == 3)) return true;

        // VZOREC 4: Centrálny pilier (Ukazovákový stretch zips 2x2 proti sebe)
        if (idx[0] == 4 && idx[1] == 5 && idx[2] == 14 && idx[3] == 15) return true;
        if (idx[0] == 14 && idx[1] == 15 && idx[2] == 24 && idx[3] == 25) return true;

        // VZOREC 1: 4 symboly v jednom riadku lineárne vedľa seba
        if (r[0] == r[1] && r[1] == r[2] && r[2] == r[3]) {
            if (c[1] == c[0] + 1 && c[2] == c[1] + 1 && c[3] == c[2] + 1) return true;
        }

        // VZOREC 2: Vertikálna symetria (Dvojičky nad sebou v rovnakých stĺpcoch)
        if (c[0] == c[1] && c[2] == c[3] && r[0] != r[1] && r[2] != r[3]) return true;
        if (c[0] == c[2] && c[1] == c[3] && r[0] != r[2] && r[1] != r[3]) return true;

        // VZOREC 3: Horizontálne zrkadlo rúk
        if (c[0] < 5 && c[1] < 5 && c[2] >= 5 && c[3] >= 5) {
            if (r[0] == r[3] && c[0] == 9 - c[3] && r[1] == r[2] && c[1] == 9 - c[2]) return true;
            if (r[0] == r[2] && c[0] == 9 - c[2] && r[1] == r[3] && c[1] == 9 - c[3]) return true;
        }

        return false;
    }

    /**
     * Vykreslí 30-klávesovú maticu. Hviezdička '*' je zamknutý symbol, bodka '.' je voľné písmeno.
     */
    private static void drawKeyboardMatrix(int[] lockedIndices) {
        char[] display = new char[30];
        Arrays.fill(display, '.');
        for (int idx : lockedIndices) {
            display[idx] = '*';
        }

        // Riadok 0
        System.out.print("  ");
        for(int i=0; i<5; i++) System.out.print(display[i] + " ");
        System.out.print("| ");
        for(int i=5; i<10; i++) System.out.print(display[i] + " ");
        System.out.println();

        // Riadok 1 (Home Row)
        System.out.print("  ");
        for(int i=10; i<15; i++) System.out.print(display[i] + " ");
        System.out.print("| ");
        for(int i=15; i<20; i++) System.out.print(display[i] + " ");
        System.out.println();

        // Riadok 2
        System.out.print("  ");
        for(int i=20; i<25; i++) System.out.print(display[i] + " ");
        System.out.print("| ");
        for(int i=25; i<30; i++) System.out.print(display[i] + " ");
        System.out.println("\n");
    }
}
