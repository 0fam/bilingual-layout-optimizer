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

        // 1. 🏎️ Single-pass zber indexov 4 symbolov
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
        // 🛑 1. ERGONOMICKÁ STRÁŽ & ZJEDNOTENÁ MASKA
        // =========================================================================
        int symbolsMask = (1 << idx0) | (1 << idx1) | (1 << idx2) | (1 << idx3);

        // Globálny test domovského riadku (int symbolsMask sa bezpečne porovná s long maskou)
        if ((symbolsMask & FORBIDDEN_HOME_ROW_MASK) != 0) {
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

        // aktivuj iba v pripade, ze chces 4bloky a pravidlo 2 je vypnute
        // =========================================================================
        // 👑 PRAVIDLO 1: Horizontálny monolit (všetky 4 symboly v jednom riadku)
        // =========================================================================
        if (r0 == r1 && r1 == r2 && r2 == r3) {
            return true;
        }

        // =========================================================================
        // 📐 PRAVIDLO 2: Sila trojice (3 symboly v tesnej línii H alebo V)
        // =========================================================================
        // Horizontálna kontrola (okno 3 bity)
//        for (int row = 0; row < 3; row++) {
//            int offset = row * 10;
//            for (int col = 0; col <= 7; col++) {
//                int m = offset + col;
//                if (((symbolsMask >> m) & 1) == 1 &&
//                        ((symbolsMask >> (m + 1)) & 1) == 1 &&
//                        ((symbolsMask >> (m + 2)) & 1) == 1) {
//                    return true;
//                }
//            }
//        }
//        // Vertikálna kontrola líniovej trojice
//        for (int col = 0; col < 10; col++) {
//            if (((symbolsMask >> col) & 1) == 1 &&
//                    ((symbolsMask >> (col + 10)) & 1) == 1 &&
//                    ((symbolsMask >> (col + 20)) & 1) == 1) {
//                return true;
//            }
//        }

        // =========================================================================
        // 🌀 PRAVIDLO 3: Zhluky a skupiny 2 + 2 (Každý symbol musí mať suseda)
        // =========================================================================
        if (hasNeighbor(r0, c0, idx0, symbolsMask) &&
                hasNeighbor(r1, c1, idx1, symbolsMask) &&
                hasNeighbor(r2, c2, idx2, symbolsMask) &&
                hasNeighbor(r3, c3, idx3, symbolsMask)) {
            return true;
        }

        // =========================================================================
        // ⚖️ PRAVIDLO 4: Izolovaná čistá symetria (Zrkadlo, Vertikála, Split)
        // =========================================================================
        // Porovnáme všetky 3 kombinácie dvojíc
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
        if (cA == cB) return true; // Vertikálne nad sebou

        // B: 🚀 ABSOLÚTNY RYCHLOSTNÝ TRÁG: Využijeme tvoje predpočítané zrkadlo z bitov!
        // Vytiahneme mirror z pA (posun 10, maska 0x1F) a pozrieme sa, či sa rovná indexu B.
        if (((pA >> 10) & 0x1F) == idxB) return true; // Zrkadlo cez tvoj mirror kartón

        // C: Paralelná split symetria (rovnaký riadok, posun presne o pol klávesnice)
        if (rA == rB && Math.abs(cA - cB) == 5) return true; // Paralelný split posun
        return false;
    }

    public static boolean isSuperHumanFriendly(char[] matrix) {
        int idx0 = -1, idx1 = -1, idx2 = -1, idx3 = -1;
        int count = 0;

        // 1. 🏎️ Single-pass zber indexov 4 symbolov
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

        // 🛑 ERGONOMICKÁ STRÁŽ
        int symbolsMask = (1 << idx0) | (1 << idx1) | (1 << idx2) | (1 << idx3);
        if ((symbolsMask & FORBIDDEN_HOME_ROW_MASK) != 0) {
            return false;
        }

        // Unpack z POS_INFO
        int p0 = POS_INFO[idx0], p1 = POS_INFO[idx1], p2 = POS_INFO[idx2], p3 = POS_INFO[idx3];
        int r0 = (p0 >> 7) & 0x3, r1 = (p1 >> 7) & 0x3, r2 = (p2 >> 7) & 0x3, r3 = (p3 >> 7) & 0x3;
        int c0 = (p0 >> 3) & 0xF, c1 = (p1 >> 3) & 0xF, c2 = (p2 >> 3) & 0xF, c3 = (p3 >> 3) & 0xF;

        // =========================================================================
        // 🌀 1. ČISTÉ ZHLUKY 2+2 ALEBO 4 (Koniec 3+1 "pustých ostrovov")
        // =========================================================================
        // Ak úplne každý symbol susedí aspoň s jedným ďalším symbolom,
        // automaticky to povolí len: súvislú 4-ku za sebou, štvorec 2x2, alebo zhluky 2+2.
        // Ak je jeden symbol sám (ako čiarka u minulého víťaza), hasNeighbor preň vráti false.
        if (hasNeighbor(r0, c0, idx0, symbolsMask) &&
                hasNeighbor(r1, c1, idx1, symbolsMask) &&
                hasNeighbor(r2, c2, idx2, symbolsMask) &&
                hasNeighbor(r3, c3, idx3, symbolsMask)) {
            return true;
        }

        // =========================================================================
        // ⚖️ 2. ČISTÁ PÁROVÁ SYMETRIA (Obe dvojice MUSIA mať navlas rovnaký typ)
        // =========================================================================
        // Kombinácia dvojíc: (0,1) a (2,3)
        int t01 = getSymType(idx0, p0, r0, c0, idx1, p1, r1, c1);
        int t23 = getSymType(idx2, p2, r2, c2, idx3, p3, r3, c3);
        if (t01 != 0 && t01 == t23) return true;

        // Kombinácia dvojíc: (0,2) a (1,3)
        int t02 = getSymType(idx0, p0, r0, c0, idx2, p2, r2, c2);
        int t13 = getSymType(idx1, p1, r1, c1, idx3, p3, r3, c3);
        if (t02 != 0 && t02 == t13) return true;

        // Kombinácia dvojíc: (0,3) and (1,2)
        int t03 = getSymType(idx0, p0, r0, c0, idx3, p3, r3, c3);
        int t12 = getSymType(idx1, p1, r1, c1, idx2, p2, r2, c2);
        if (t03 != 0 && t03 == t12) return true;

        return false; // Absolútna poprava pre akýkoľvek iný vizuálny chaos
    }

    private static int getSymType(int idxA, int pA, int rA, int cA, int idxB, int pB, int rB, int cB) {
        if (cA == cB) return 1; // 1 = Vertikálna symetria (nad sebou)
        if (((pA >> 10) & 0x1F) == idxB) return 2; // 2 = Zrkadlová prstová symetria
        if (rA == rB && Math.abs(cA - cB) == 5) return 3; // 3 = Paralelná split symetria

        // 🌌 NOVINKA: Stredová bodová symetria (Rotácia o 180° okolo stredu klávesnice)
        if ((rA + rB == 2) && (cA + cB == 9)) return 4; // 4 = Dokonalý stredový bodový balans

        return 0; // 0 = Žiadna symetria
    }

    public static boolean isHumanFriendlyUnicorn(char[] matrix) {
        int idx0 = -1, idx1 = -1, idx2 = -1, idx3 = -1;
        int count = 0;

        // 1. 🏎️ Single-pass zber indexov 4 symbolov
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

        // 🛑 ERGONOMICKÁ STRÁŽ
        int symbolsMask = (1 << idx0) | (1 << idx1) | (1 << idx2) | (1 << idx3);
        if ((symbolsMask & FORBIDDEN_HOME_ROW_MASK) != 0) {
            return false;
        }

        // Unpack z POS_INFO kartónu
        int p0 = POS_INFO[idx0], p1 = POS_INFO[idx1], p2 = POS_INFO[idx2], p3 = POS_INFO[idx3];
        int r0 = (p0 >> 7) & 0x3, r1 = (p1 >> 7) & 0x3, r2 = (p2 >> 7) & 0x3, r3 = (p3 >> 7) & 0x3;
        int c0 = (p0 >> 3) & 0xF, c1 = (p1 >> 3) & 0xF, c2 = (p2 >> 3) & 0xF, c3 = (p3 >> 3) & 0xF;

        // =========================================================================
        // 🌀 MÓD 1: SÚVISLÝ TETRIS BLOK (Hadíky, štvorce 2x2, L-ka, T-čka v jednom kuse)
        // =========================================================================
        int visitedMask = 1 << idx0;
        for (int step = 0; step < 3; step++) {
            int newBits = 0;
            if (((visitedMask >> idx0) & 1) == 1) newBits |= getNeighborMask(idx0, symbolsMask);
            if (((visitedMask >> idx1) & 1) == 1) newBits |= getNeighborMask(idx1, symbolsMask);
            if (((visitedMask >> idx2) & 1) == 1) newBits |= getNeighborMask(idx2, symbolsMask);
            if (((visitedMask >> idx3) & 1) == 1) newBits |= getNeighborMask(idx3, symbolsMask);
            visitedMask |= newBits;
        }

        // Ak tvoria jeden prepojený blok, layout okamžite prechádza (Netreba riešiť symetriu)
        if (Integer.bitCount(visitedMask) == 4) {
            return true;
        }

        // =========================================================================
        // 🚨 TOTO TU SUROVO VLEP: STOP OSAMOTENÝM SKOKANOM (Koniec riadkových podvodov)
        // =========================================================================
        // Ak čo i len jeden zo 4 symbolov vráti masku susedov rovnú 0, znamená to,
        // že je kompletne izolovaný abecedou. V tom momente layout okamžite popravíme.
        if (getNeighborMask(idx0, symbolsMask) == 0 ||
                getNeighborMask(idx1, symbolsMask) == 0 ||
                getNeighborMask(idx2, symbolsMask) == 0 ||
                getNeighborMask(idx3, symbolsMask) == 0) {
            return false;
        }

        // =========================================================================
        // ⚖️ MÓD 2: UNIFORMNE SYMETRICKÝ JEDNOROŽEC (Izolované alebo 2+2, ale s rovnakým typom symetrie)
        // =========================================================================
        // Kombinácia dvojíc: (0,1) a (2,3)
        int t01 = getSymType(idx0, p0, r0, c0, idx1, p1, r1, c1);
        int t23 = getSymType(idx2, p2, r2, c2, idx3, p3, r3, c3);
        if (t01 != 0 && t01 == t23) return true;

        // Kombinácia dvojíc: (0,2) a (1,3)
        int t02 = getSymType(idx0, p0, r0, c0, idx2, p2, r2, c2);
        int t13 = getSymType(idx1, p1, r1, c1, idx3, p3, r3, c3);
        if (t02 != 0 && t02 == t13) return true;

        // Kombinácia dvojíc: (0,3) and (1,2)
        int t03 = getSymType(idx0, p0, r0, c0, idx3, p3, r3, c3);
        int t12 = getSymType(idx1, p1, r1, c1, idx2, p2, r2, c2);
        if (t03 != 0 && t03 == t12) return true;

        return false; // Absolútna poprava pre akýkoľvek iný asymetrický rozsyp
    }

    private static int getNeighborMask(int idx, int mask) {
        int p = POS_INFO[idx];
        int r = (p >> 7) & 0x3;
        int c = (p >> 3) & 0xF;
        int neighbors = 0;

        if (r > 0 && ((mask >> (idx - 10)) & 1) == 1) neighbors |= (1 << (idx - 10)); // Hore
        if (r < 2 && ((mask >> (idx + 10)) & 1) == 1) neighbors |= (1 << (idx + 10)); // Dole
        if (c > 0 && ((mask >> (idx - 1))  & 1) == 1) neighbors |= (1 << (idx - 1));  // Vľavo
        if (c < 9 && ((mask >> (idx + 1))  & 1) == 1) neighbors |= (1 << (idx + 1));  // Vpravo

        return neighbors;
    }
}
