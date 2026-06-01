package mh.layout.visualizer;

import java.util.Arrays;

public class LayoutExplorer {

    // Naša známa šablóna zakázaných prstov na Home Row (11,12,13 a 16,17,18)
    private static final long FORBIDDEN_HOME_ROW_MASK =
            (1L << 11) | (1L << 12) | (1L << 13) | (1L << 16) | (1L << 17) | (1L << 18);

    // Statické pole zrkadiel pre rýchlu kontrolu symetrie
    private static final int[] MIRROR = {
            9,  8,  7,  6,  5,   4,  3,  2,  1,  0,
            19, 18, 17, 16, 15,  14, 13, 12, 11, 10,
            29, 28, 27, 26, 25,  24, 23, 22, 21, 20
    };

    public static void main(String[] args) {
        int totalChecked = 0;
        int validCount = 0;

        // 4-úrovňový cyklus, ktorý bezpečne vygeneruje všetkých 27 405 kombinácií (bez duplicít)
        for (int i = 0; i < 27; i++) {
            for (int j = i + 1; j < 28; j++) {
                for (int k = j + 1; k < 29; k++) {
                    for (int l = k + 1; l < 30; l++) {
                        totalChecked++;

                        if (isGeometryFriendly(i, j, k, l)) {
                            validCount++;
                            printLayout(validCount, i, j, k, l);
                        }
                    }
                }
            }
        }

        System.out.println("=========================================");
        System.out.println(" Celkovo preskúmaných kombinácií: " + totalChecked);
        System.out.println(" Ergonomicky vyhovujúcich kombinácií: " + validCount);
        System.out.println("=========================================");
    }

    // Pure Geometrický filter – upravený presne podľa tvojich pravidiel
    private static boolean isGeometryFriendly(int idx0, int idx1, int idx2, int idx3) {

        // 1. GLOBÁLNA STRÁŽ HOME ROW
        long layoutSymbolsMask = (1L << idx0) | (1L << idx1) | (1L << idx2) | (1L << idx3);
        if ((layoutSymbolsMask & FORBIDDEN_HOME_ROW_MASK) != 0) {
            return false; // Kolízia s hlavnými prstami na základni
        }

        // 2. MÓD 3: STREDOVÉ STRETCHE (Uvoľnený zips ukazovákov - stĺpce 4 a 5)
        long centerColumnsMask = 0x300C030L;
        if ((layoutSymbolsMask & ~centerColumnsMask) == 0) {
            return true; // Sú kompletne v stredovom zipse? Majú absolútnu voľnosť!
        }

        // Pomocná extrakcia stĺpcov pre Mód 1
        int c0 = idx0 % 10;
        int c1 = idx1 % 10;
        int c2 = idx2 % 10;
        int c3 = idx3 % 10;

        // 3. 👑 MÓD 1: JEDNORUČNÝ BLOK (Prísne na PRAVEJ ruke)
        if (c0 >= 5 && c1 >= 5 && c2 >= 5 && c3 >= 5) {
            return true;
        }

        // 4. 📐 MÓD 2: OBOJRUČNÉ ROZLOŽENIE -> Čistá zrkadlovosť
        int m0 = MIRROR[idx0];
        int m1 = MIRROR[idx1];
        int m2 = MIRROR[idx2];
        int m3 = MIRROR[idx3];

        return (m0 == idx1 || m0 == idx2 || m0 == idx3) &&
                (m1 == idx0 || m1 == idx2 || m1 == idx3) &&
                (m2 == idx0 || m2 == idx1 || m2 == idx3) &&
                (m3 == idx0 || m3 == idx1 || m3 == idx2);
    }

    // Pomocná metóda, ktorá ti v konzole nakreslí Totem mriežku
    private static void printLayout(int id, int idx0, int idx1, int idx2, int idx3) {
        System.out.println("\n# CONFIGURATION " + id + " -> Indexy: [" + idx0 + ", " + idx1 + ", " + idx2 + ", " + idx3 + "]");

        for (int row = 0; row < 3; row++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int col = 0; col < 10; col++) {
                int currentIdx = row * 10 + col;

                // Vizuálny predel medzi ľavou a pravou rukou
                if (col == 5) sb.append("| ");

                if (currentIdx == idx0 || currentIdx == idx1 || currentIdx == idx2 || currentIdx == idx3) {
                    sb.append("[#] "); // Symbol
                } else if (currentIdx == 11 || currentIdx == 12 || currentIdx == 13 ||
                        currentIdx == 16 || currentIdx == 17 || currentIdx == 18) {
                    sb.append(" .  "); // Chránená Home Row základňa
                } else {
                    sb.append("[ ] "); // Voľné miesto pre abecedu
                }
            }
            System.out.println(sb.toString());
        }
    }
}
