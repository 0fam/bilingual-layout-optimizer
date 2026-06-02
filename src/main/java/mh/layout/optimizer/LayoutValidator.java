package mh.layout.optimizer;

import static mh.layout.topology.KeyboardTopology.*;

public final class LayoutValidator {

//    public static boolean isHumanFriendly(char[] matrix) {
//        int row = 1;
//        int offset = row * 10;
//
//        for (int col = 1; col < 9; col++) {
//            if (col == 4 || col == 5) continue; // Stredné stretch stĺpce nechávame na voľný experiment
//
//            char ch = matrix[offset + col];
//
//            // ZÁKAZ SYMBOLOV: Na hlavných 8 klávesoch domovského riadku musí byť iba čistá abeceda
//            if (ch < 'A' || ch > 'Z') return false;
//
//            // ⚡ EARLY PRUNING: Vyhadzujeme len absolútne dno korpusu (Q, W, X).
//            if (ch == 'Q' || ch == 'W' || ch == 'X') return false;
//        }
//
//        return true;
//    }

    //    public static boolean isHumanFriendly(char[] matrix) {
//        for (int row = 0; row < 3; row++) {
//            int offset = row * 10;
//
//            for (int col = 0; col < 10; col++) {
//                char ch = matrix[offset + col];
//
//                // 1. Ak je to písmeno, nezaujíma nás to
//                if (ch >= 'A' && ch <= 'Z') continue;
//
//                // 2. VOĽNÉ STĹPCE: Tu symbol môže stáť úplne sám
//                if (col == 0 || col == 4 || col == 5 || col == 9) continue;
//
//                // 3. PODMIENENÉ STĹPCE
//                char left = matrix[offset + col - 1];
//                if (left < 'A' || left > 'Z') continue;
//                char right = matrix[offset + col + 1];
//                if (right < 'A' || right > 'Z') continue;
//                return false;
//            }
//        }
//        return true; // Všetky symboly sú buď na krajoch/stredoch, alebo v pároch
//    }

//    public static boolean isHumanFriendly(char[] matrix) {
//        for (int row = 0; row < 3; row++) {
//            int offset = row * 10;
//
//            for (int col = 0; col < 10; col++) {
//                char ch = matrix[offset + col];
//
//                // =========================================================================
//                // 🚨 ŠPECIÁLNA STRÁŽ PRE HOME ROW (Riadok 1) - Koncept prioritných klávesov
//                // =========================================================================
//                if (row == 1) {
//                    // Definuje odpad, ktorý na Home Row nechceme vidieť ani na sekundu
//                    boolean isTrashKey = (ch == 'Q' || ch == 'X' || ch == 'W' || ch == 'J' || ch == 'Z');
//
//                    if (col == 4 || col == 5) {
//                        // STRETCH STĹPCE: Povolené písmená (mimo trashu) ALEBO bodka a čiarka
//                        if (isTrashKey) return false;
//                        if (ch < 'A' || ch > 'Z') {
//                            if (ch != '.' && ch != ',') return false; // Slabé symboly do stredu nepustíme
//                        }
//                    } else {
//                        // HLAVNÉ POZÍCIE PRSTOV: Prísne iba čistá abeceda bez trashu a symbolov
//                        if (ch < 'A' || ch > 'Z' || isTrashKey) return false;
//                    }
//                    continue; // Home row vyriešená, ideme na ďalšiu klávesu
//                }
//
//                // =========================================================================
//                // 📐 PRAVIDLO PRE HORNÝ A SPODNÝ RIADOK (Riadky 0 a 2)
//                // =========================================================================
//                if (ch >= 'A' && ch <= 'Z') continue; // Písmená hore/dole neriešime
//                if (col == 0 || col == 4 || col == 5 || col == 9) continue; // Voľné stĺpce
//
//                // Podmienené stĺpce hore a dole: symbol musí mať vedľa seba parťáka
//                char left = matrix[offset + col - 1];
//                if (left < 'A' || left > 'Z') continue;
//
//                char right = matrix[offset + col + 1];
//                if (right < 'A' || right > 'Z') continue;
//
//                return false; // Osamotený symbol v riadkoch 0 alebo 2 -> KO
//            }
//        }
//        return true;
//    }

//    public static boolean isHumanFriendly(char[] matrix) {
//        int idx0 = -1, idx1 = -1, idx2 = -1, idx3 = -1;
//        int count = 0;
//
//        // 1. Single-pass zber indexov (automaticky zoradené 0-29)
//        for (int i = 0; i < KEY_COUNT; i++) {
//            char c = matrix[i];
//            if (c < 'A' || c > 'Z') {
//                if (count == 0) idx0 = i;
//                else if (count == 1) idx1 = i;
//                else if (count == 2) idx2 = i;
//                else if (count == 3) { idx3 = i; break; }
//                count++;
//            }
//        }
//
//        // =========================================================================
//        // 🛑 GLOBÁLNA STRÁŽ HOME ROW: Základný test pred všetkými módmi
//        // =========================================================================
//        // Vygenerujeme 30-bitovú masku polôh symbolov aktuálneho layoutu
//        long layoutSymbolsMask = (1L << idx0) | (1L << idx1) | (1L << idx2) | (1L << idx3);
//
//        // Porovnáme to s kartónom z KeyboardTopology (bity 11,12,13 a 16,17,18)
//        // Ak sa bity pretnú, symbol parazituje na jadrových prstoch -> OKAMŽITÁ POPRAVA
//        if ((layoutSymbolsMask & FORBIDDEN_HOME_ROW_MASK) != 0) {
//            return false; // OKAMŽITÁ POPRAVA: Symbol parazituje na domovských prstoch
//        }
//
//        // =========================================================================
//        // ⚡ MÓD 3: STREDOVÉ STRETCHE (Symboly sú schované v zipse ukazovákov)
//        // =========================================================================
//        // Maska pre stredové stĺpce 4 a 5 (zapnuté bity pre indexy: 4, 5, 14, 15, 24, 25)
//        long centerColumnsMask = 0x300C030L;
//
//        // Bitový AND s negáciou masky. Ak vyjde 0, znamená to, že ŽIADNY symbol nevyliezol mimo stred.
//        if ((layoutSymbolsMask & ~centerColumnsMask) == 0) {
//            return true; // Sú kompletne v stredovom zipse? Majú absolútnu voľnosť, kašleme na symetriu!
//        }
//
//        // 2. Extrakcia rúk z POS_INFO (Maska 0x200 izoluje 9. bit)
//        int hand0 = POS_INFO[idx0] & 0x200;
//        int hand1 = POS_INFO[idx1] & 0x200;
//        int hand2 = POS_INFO[idx2] & 0x200;
//        int hand3 = POS_INFO[idx3] & 0x200;
//
//        // =========================================================================
//        // 👑 MÓD 1: JEDNORUČNÝ BLOK (Všetky symboly majú rovnakú ruku)
//        // =========================================================================
//        if (hand0 == hand1 && hand0 == hand2 && hand0 == hand3) {
//            return true; // Na jednej ruke mimo zakázanú Home Row? Absolútna sloboda.
//        }
//
//        // =========================================================================
//        // 📐 MÓD 2: OBOJRUČNÉ ROZLOŽENIE -> Čistá, nekompromisná zrkadlovosť
//        // =========================================================================
//        // Vytiahneme predpočítané finálne indexy zrkadiel (posun o 10 bitov a maska 0x1F)
//        int m0 = (POS_INFO[idx0] >> 10) & 0x1F;
//        int m1 = (POS_INFO[idx1] >> 10) & 0x1F;
//        int m2 = (POS_INFO[idx2] >> 10) & 0x1F;
//        int m3 = (POS_INFO[idx3] >> 10) & 0x1F;
//
//        return (m0 == idx1 || m0 == idx2 || m0 == idx3) &&
//                (m1 == idx0 || m1 == idx2 || m1 == idx3) &&
//                (m2 == idx0 || m2 == idx1 || m2 == idx3) &&
//                (m3 == idx0 || m3 == idx1 || m3 == idx2);
//    }

    public static boolean isHumanFriendly(char[] matrix) {
        int idx0 = -1, idx1 = -1, idx2 = -1, idx3 = -1;
        int count = 0;

        // 1. 🏎️ Bleskový zber indexov 4 symbolov (Single-pass)
        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            if (c < 'A' || c > 'Z') {
                if (count == 0) idx0 = i;
                else if (count == 1) idx1 = i;
                else if (count == 2) idx2 = i;
                else if (count == 3) { idx3 = i; break; }
                count++;
            }
        }

        // =========================================================================
        // 🛑 1. ERGONOMICKÁ STRÁŽ: Ochrana hlavných prstov domovského riadku
        // =========================================================================
        long layoutSymbolsMask = (1L << idx0) | (1L << idx1) | (1L << idx2) | (1L << idx3);
        if ((layoutSymbolsMask & FORBIDDEN_HOME_ROW_MASK) != 0) {
            return false;
        }

        // =========================================================================
        // 📦 BIT-UNPACKING: Vytiahneme 2D súradnice priamo z tvojej mapy POS_INFO
        // =========================================================================
        int p0 = POS_INFO[idx0], p1 = POS_INFO[idx1], p2 = POS_INFO[idx2], p3 = POS_INFO[idx3];

        // Riandky (posun 7, maska 2 bity = 0x3)
        int r0 = (p0 >> 7) & 0x3, r1 = (p1 >> 7) & 0x3, r2 = (p2 >> 7) & 0x3, r3 = (p3 >> 7) & 0x3;
        // Stĺpce (posun 3, maska 4 bity = 0xF)
        int c0 = (p0 >> 3) & 0xF, c1 = (p1 >> 3) & 0xF, c2 = (p2 >> 3) & 0xF, c3 = (p3 >> 3) & 0xF;

        int symbolPresenceMask = (1 << idx0) | (1 << idx1) | (1 << idx2) | (1 << idx3);

        // =========================================================================
        // 👑 PRAVIDLO 1: Horizontálny monolit (všetky 4 symboly v jednom riadku)
        // =========================================================================
        if (r0 == r1 && r1 == r2 && r2 == r3) {
            return true;
        }

        // =========================================================================
        // 📐 PRAVIDLO 2: Sila trojice (3 symboly v tesnej línii H alebo V)
        // =========================================================================
        // Horizontálna kontrola líniovej trojice
        for (int row = 0; row < 3; row++) {
            int offset = row * 10;
            for (int col = 0; col <= 7; col++) {
                int m = offset + col;
                if (((symbolPresenceMask >> m) & 1) == 1 &&
                        ((symbolPresenceMask >> (m + 1)) & 1) == 1 &&
                        ((symbolPresenceMask >> (m + 2)) & 1) == 1) {
                    return true;
                }
            }
        }
        // Vertikálna kontrola líniovej trojice
        for (int col = 0; col < 10; col++) {
            if (((symbolPresenceMask >> col) & 1) == 1 &&
                    ((symbolPresenceMask >> (col + 10)) & 1) == 1 &&
                    ((symbolPresenceMask >> (col + 20)) & 1) == 1) {
                return true;
            }
        }

        // =========================================================================
        // 🌀 PRAVIDLO 3: Zhluky a skupiny 2 + 2 (Každý symbol musí mať suseda)
        // =========================================================================
        if (hasNeighbor(r0, c0, idx0, symbolPresenceMask) &&
                hasNeighbor(r1, c1, idx1, symbolPresenceMask) &&
                hasNeighbor(r2, c2, idx2, symbolPresenceMask) &&
                hasNeighbor(r3, c3, idx3, symbolPresenceMask)) {
            return true;
        }

        // =========================================================================
        // ⚖️ PRAVIDLO 4: Izolovaná čistá symetria (Zrkadlo, Vertikála, Split)
        // =========================================================================
        // Porovnáme všetky 3 kombinácie dvojíc cez tvoj nový bitový skener
        if ((isBitSymmetric(idx0, p0, r0, c0, idx1, p1, r1, c1) && isBitSymmetric(idx2, p2, r2, c2, idx3, p3, r3, c3)) ||
                (isBitSymmetric(idx0, p0, r0, c0, idx2, p2, r2, c2) && isBitSymmetric(idx1, p1, r1, c1, idx3, p3, r3, c3)) ||
                (isBitSymmetric(idx0, p0, r0, c0, idx3, p3, r3, c3) && isBitSymmetric(idx1, p1, r1, c1, idx2, p2, r2, c2))) {
            return true;
        }

        return false;
    }

    // 🧬 Pomocná blesková mikro-metóda na kontrolu priamych susedov
    private static boolean hasNeighbor(int r, int c, int idx, int mask) {
        if (r > 0 && ((mask >> (idx - 10)) & 1) == 1) return true; // Hore
        if (r < 2 && ((mask >> (idx + 10)) & 1) == 1) return true; // Dole
        if (c > 0 && ((mask >> (idx - 1))  & 1) == 1) return true; // Vľavo
        if (c < 9 && ((mask >> (idx + 1))  & 1) == 1) return true; // Vpravo
        return false;
    }

    // 🪞 Tvoj posilnený symetrický skener využívajúci predpočítané zrkadlo z bitov
    private static boolean isBitSymmetric(int idxA, int pA, int rA, int cA, int idxB, int pB, int rB, int cB) {
        // A: Vertikálne presne nad sebou (stĺpce lícujú)
        if (cA == cB) return true;

        // B: 🚀 ABSOLÚTNY RYCHLOSTNÝ TRÁG: Využijeme tvoje predpočítané zrkadlo z bitov!
        // Vytiahneme mirror z pA (posun 10, maska 0x1F) a pozrieme sa, či sa rovná indexu B.
        if (((pA >> 10) & 0x1F) == idxB) return true;

        // C: Paralelná split symetria (rovnaký riadok, posun presne o pol klávesnice)
        if (rA == rB && Math.abs(cA - cB) == 5) return true;

        return false;
    }

}
