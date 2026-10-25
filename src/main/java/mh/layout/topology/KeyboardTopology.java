package mh.layout.topology;

public final class KeyboardTopology {

    public static final int LEFT_HAND = 0, RIGHT_HAND = 1;
    public static final int TOP_ROW = 0, HOME_ROW = 1, BOTTOM_ROW = 2;
    public static final int KEY_COUNT = 30;
    // cisla klaves na ktorych nemozu byt symboly - cize HOME ROW bez malickov napriklad, t.j. 11, 12, 13, 16, 17, 18
    public static final long FORBIDDEN_HOME_ROW_MASK = (1L << 11) | (1L << 12) | (1L << 13) | (1L << 16) | (1L << 17) | (1L << 18);

    // 🎯 HLAVNÉ BITOVÉ POLE PRE ZÁKLADNÚ ANATÓMIU
    public static final int[] POS_INFO = new int[KEY_COUNT];

    // 🚀 2D LOOKUP MATICE PRE BIGRAMY (Odpája kompletnú logiku z horúcej slučky)
    public static final boolean[][] IS_ALT = new boolean[KEY_COUNT][KEY_COUNT];
    public static final boolean[][] IS_ASFB_DOWN = new boolean[KEY_COUNT][KEY_COUNT]; // Adjacent Same Finger Bigram
    public static final boolean[][] IS_ASFB_UP = new boolean[KEY_COUNT][KEY_COUNT];
    public static final boolean[][] IS_RSSFB = new boolean[KEY_COUNT][KEY_COUNT]; // Row Skip Same Finger Bigram
    public static final boolean[][] IS_SSFB = new boolean[KEY_COUNT][KEY_COUNT]; // Stretch Same Finger Bigram
    public static final boolean[][] IS_SCISSORS = new boolean[KEY_COUNT][KEY_COUNT];
    public static final boolean[][] IS_LSB = new boolean[KEY_COUNT][KEY_COUNT];
    public static final boolean[][] IS_LHB = new boolean[KEY_COUNT][KEY_COUNT];
    public static final boolean[][] IS_SFS = new boolean[KEY_COUNT][KEY_COUNT];

    // 🚀 FLAT POĽA PRE TRIGRAMY (Sťahuje 27 000 iterácií na čistých 6 750)
    public static int VALID_TRIPLES_COUNT = 0;
    public static final int[] TRIPLE_I = new int[6750];
    public static final int[] TRIPLE_J = new int[6750];
    public static final int[] TRIPLE_K = new int[6750];
    public static final byte[] TRIPLE_TYPE = new byte[6750]; // 1 = ROLL_IN, 2 = ROLL_OUT, 3 = REDIRECT
    public static final boolean[][][] IS_SFT = new boolean[KEY_COUNT][KEY_COUNT][KEY_COUNT]; // Same Finger Trigram

    /**
     * Statická penalizácia pozícií, váha nepohodlia/natiahnutia (Effort / Stretch Weight)
     * Čím nižšie číslo, tým prirodzenejší a komfortnejší stlač klávesu.
     * Základný domovský riadok (silné prsty) = 1.00 (dokonalý komfort).
     */
//    public static final double[] POS_STRETCH_WEIGHT = {
//            // HORNÝ RIADKOK: Prirodzené natiahnutie prstov smerom von.
//            // Malíček je kratší (1.6), ukazovák a prostredník natiahneš najľahšie (1.3).
//            // Indexy 4 a 5 sú vnútorné diagonálne streče (1.80) - stredne ťažké.
//            1.60, 1.40, 1.30, 1.30, 1.80,   1.80, 1.30, 1.30, 1.40, 1.60,
//
//            // DOMOVSKÝ RIADOK: Prsty na ňom reálne ležia.
//            // Silné prsty = 1.00. Malíček má menší offset (1.15).
//            // Indexy 14 a 15 sú bočné streče ukazovákov do stredu klávesnice (1.50).
//            1.15, 1.00, 1.00, 1.00, 1.50,   1.50, 1.00, 1.00, 1.00, 1.15,
//
//            // SPODNÝ RIADOK: Krčenie prstov pod seba. Ergonomicky najnepríjemnejší riadok.
//            // Prstenník (1.70) a malíček (1.90) trpia najviac, lebo zdieľajú šľachy.
//            // Indexy 24 a 25 sú najbrutálnejšie vnútorné spodné diagonály (2.10).
//            1.90, 1.70, 1.40, 1.40, 2.10,   2.10, 1.40, 1.40, 1.70, 1.90
//    };

    public static final double[] POS_STRETCH_WEIGHT = {
//                   Ľavá ruka                       Pravá ruka
//           P     R     M     I    Stretch Stretch   I     M     R     P
                    4.0, 2.2, 1.8, 1.9,  5,    5,  1.9, 1.8, 2.2, 4.0,
                    1.2, 1.1, 1.0, 1.0, 10,   10,  1.0, 1.0, 1.1, 1.2,
                    4.5, 2.5, 1.6, 1.5, 20,   20,  1.5, 1.6, 2.5, 4.5

//            1.60, 1.50, 1.20, 1.40,  4.00,    4.00,  1.40, 1.20, 1.50, 1.60, // HORNÝ
//            1.15, 1.05, 1.00, 1.00,  8.00,    8.00,  1.00, 1.00, 1.05, 1.15, // DOMOVSKÝ
//            1.80, 1.70, 1.30, 1.25, 12.00,   12.00,  1.25, 1.30, 1.70, 1.80  // SPODNÝ

//            1.70, 1.45, 1.20, 1.40,  2.10,    2.10,  1.40, 1.20, 1.45, 1.70, // HORNÝ
//            1.15, 1.05, 1.00, 1.00,  2.40,    2.40,  1.00, 1.00, 1.05, 1.15, // DOMOVSKÝ
//            1.80, 1.70, 1.40, 1.35,  2.70,    2.70,  1.35, 1.40, 1.70, 1.80  // SPODNÝ

//            1.75, 1.45, 1.20, 1.50,  2.10,    2.10,  1.50, 1.20, 1.45, 1.75, // HORNÝ
//            1.15, 1.05, 1.00, 1.00,  2.40,    2.40,  1.00, 1.00, 1.05, 1.15, // DOMOVSKÝ
//            1.85, 1.70, 1.40, 1.30,  2.70,    2.70,  1.30, 1.40, 1.70, 1.85  // SPODNÝ
    };

//    public static final double[] POS_STRETCH_WEIGHT = {
//            // Ľavá ruka                             // Pravá ruka
//            // P   R     M     I       Stretch     I     M     R     P
//            1.60, 1.40, 1.30, 1.30, 1.90,   1.90, 1.30, 1.30, 1.40, 1.60, // HORNÝ
//            1.15, 1.00, 1.00, 1.00, 1.50,   1.50, 1.00, 1.00, 1.00, 1.15, // DOMOVSKÝ
//            1.75, 1.70, 1.40, 1.40, 2.10,   2.10, 1.40, 1.40, 1.70, 1.75  // SPODNÝ
//    };
//
    private KeyboardTopology() {}

    static {
        // Čitateľné polia pre človeka, ktoré pri štarte bleskovo zabalíme
        int[] hands = {
                0, 0, 0, 0, 0,  1, 1, 1, 1, 1,
                0, 0, 0, 0, 0,  1, 1, 1, 1, 1,
                0, 0, 0, 0, 0,  1, 1, 1, 1, 1
        };

        int[] rows = {
                0, 0, 0, 0, 0,  0, 0, 0, 0, 0,
                1, 1, 1, 1, 1,  1, 1, 1, 1, 1,
                2, 2, 2, 2, 2,  2, 2, 2, 2, 2
        };

        int[] cols = {
                0, 1, 2, 3, 4,  5, 6, 7, 8, 9,
                0, 1, 2, 3, 4,  5, 6, 7, 8, 9,
                0, 1, 2, 3, 4,  5, 6, 7, 8, 9
        };

        int[] fingers = {
                0, 1, 2, 3, 3,  4, 4, 5, 6, 7,
                0, 1, 2, 3, 3,  4, 4, 5, 6, 7,
                0, 1, 2, 3, 3,  4, 4, 5, 6, 7
        };

        // 🪞 TVOJE FINÁLNE INDEXY ZRKADIEL (Čisté hodnoty 0 až 29 pod sebou)
        int[] mirror = {
                9,  8,  7,  6,  5,   4,  3,  2,  1,  0,  // Horný riadok
                19, 18, 17, 16, 15,  14, 13, 12, 11, 10, // Home Row
                29, 28, 27, 26, 25,  24, 23, 22, 21, 20  // Spodný riadok
        };

        // ⚡ BIT-PACKING: Zabalíme 4 informácie do jedného čísla int
        for (int i = 0; i < KEY_COUNT; i++) {
            POS_INFO[i] = (mirror[i] << 10) | (hands[i] << 9) | (rows[i] << 7) | (cols[i] << 3) | fingers[i];
        }

        // =========================================================================
        // 🛠️ PRE-KOMPILÁCIA BIGRAMOV & SKIPGRAMOV (Spustí sa iba raz pri boote aplikácie)
        // =========================================================================
        // Prechádzame maticu všetkých fyzických slotov klávesnice (30 x 30 = 900 dvojíc).
        // DÔLEŽITÉ: i a j sú indexy PLASTOVÝCH DIER (0 až 29), nie konkrétne písmená!
        for (int i = 0; i < KEY_COUNT; i++) {
            int info1 = POS_INFO[i];
            int hand1   = (info1 >> 9) & 0x1;
            int row1    = (info1 >> 7) & 0x3;
            int col1    = (info1 >> 3) & 0xF;
            int finger1 = info1 & 0x7;

            for (int j = 0; j < KEY_COUNT; j++) {
                int info2 = POS_INFO[j];
                int hand2   = (info2 >> 9) & 0x1;
                int row2    = (info2 >> 7) & 0x3;
                int col2    = (info2 >> 3) & 0xF;
                int finger2 = info2 & 0x7;

                // A. BIOMECHANICKÉ VZŤAHY PRE ŠTANDARDNÉ BIGRAMY (Susedné údery)
                if (hand1 != hand2) {
                    // Ak je slot 'i' na opačnej ruke ako slot 'j', stlačenie týchto dvoch
                    // klávesov za sebou VŽDY vyvolá alternáciu (striedanie rúk).
                    IS_ALT[i][j] = true;
                } else {
                    // Sme na rovnakej ruke. Mapujeme ergonomické prehrešky:

                    // 1. Same Finger Bigram (SFB): Ak sloty i a j obsluhuje ten istý prst.
                    if (finger1 == finger2 && i != j) {

                        if (Math.abs(row1 - row2) >= 2) {
                            IS_RSSFB[i][j] = true;
                        } else {
                            if (row1 < row2) {
                                IS_ASFB_DOWN[i][j] = true;
                            } else {
                                IS_ASFB_UP[i][j] = true;
                            }
                        }

                        // stretch sa zapocitava ZNOVA, aby bola znasobena jeho skodlivost
                        if (col1 != col2) {
                            IS_SSFB[i][j] = true;
                        }
                    }

                    // 2. SCISSORS (Nožnice): Prsty skáču krížom cez riadok (rozdiel riadkov >= 2)
                    // a zároveň ide o anatomicky susediace prsty (rozdiel prstov je presne 1).
                    if (Math.abs(row1 - row2) >= 2 && Math.abs(finger1 - finger2) == 1) {
                        IS_SCISSORS[i][j] = true;
                    }

                    // 3. Lateral Stretch Bigram (LSB): Jeden z klávesov je vo vnútornom stĺpci (4 alebo 5),
                    // ale stláčajú ho dva rôzne prsty (asymetrické napätie v dlani).
                    boolean s1 = (col1 == 4 || col1 == 5);
                    boolean s2 = (col2 == 4 || col2 == 5);
                    if (s1 != s2 && finger1 != finger2) {
                        IS_LSB[i][j] = true;
                    }

                    // 4. Lateral Hand Bigram (LHB): Veľká únava vonkajšej hrany ruky.
                    // Kombinácia malíček + prstenník v akomkoľvek poradí na ľavej alebo pravej ruke.
                    if ((finger1 == 0 && finger2 == 1) || (finger1 == 1 && finger2 == 0) ||
                            (finger1 == 7 && finger2 == 6) || (finger1 == 6 && finger2 == 7)) {
                        IS_LHB[i][j] = true;
                    }
                }

                // B. BIOMECHANICKÉ VZŤAHY PRE SKIPGRAMY (Sendviče - SFS)
                // Sledujeme údery oddelené jedným náhodným znakom (napr. "A-X-A").
                // Ak sú sloty 'i' a 'j' na rovnakej ruke a rovnakom prste, je to Sendvič (SFS).
                if (hand1 == hand2 && finger1 == finger2 && i != j) {
                    IS_SFS[i][j] = true;
                }
            }
        }

        // =========================================================================
        // 🛠️ PRE-KOMPILÁCIA TRIGRAMOV (Kompresia 27 000 kombinácií na 6 750 platných)
        // =========================================================================
        // Otvárame teoretickú 3D kocku všetkých pozícií (30 x 30 x 30 = 27 000).
        // Naším cieľom je nájsť iba tie trojice slotov, ktoré sa dajú napísať CELÉ JEDNOU RUKOU.
        for (int i = 0; i < KEY_COUNT; i++) {
            int info1 = POS_INFO[i];
            int hand1 = (info1 >> 9) & 0x1;
            int f1    = info1 & 0x7;

            for (int j = 0; j < KEY_COUNT; j++) {
                int info2 = POS_INFO[j];
                int hand2 = (info2 >> 9) & 0x1;
                int f2    = info2 & 0x7;

                // 🛑 FILTER 1: Ak slot 'i' a slot 'j' nie sú na rovnakej ruke, ruka sa strieda.
                // Celá táto vetva vnútornej slučky je pre jednoručné trigramy (Rolls/Redirects) pasé.
                if (hand1 != hand2) continue; // Early exit z prvej ruky

                for (int k = 0; k < KEY_COUNT; k++) {
                    int info3 = POS_INFO[k];
                    int hand3 = (info3 >> 9) & 0x1;
                    int f3    = info3 & 0x7;

                    // 🛑 FILTER 2: Tretí slot 'k' musí dopadnúť na tú istú ruku ako prvé dva.
                    if (hand2 != hand3) continue;

                    byte type = 0; // Dočasný biomechanický štítok (1 = RollIn, 2 = RollOut, 3 = Redirect, 4 = Same Finger Trigram)

                    // 🌊 ROLLS (Plynulé vlny prstov v jednom smere)
                    if (f1 < f2 && f2 < f3) {
                        // Prsty tečú smerom od malíčka k ukazováku.
                        // Na ľavej ruke je to smer dnu (RollIn), na pravej ruke smer von (RollOut).
                        type = (byte) (hand1 == LEFT_HAND ? 1 : 2);
                    }
                    else if (f1 > f2 && f2 > f3) {
                        // Prsty tečú smerom od ukazováka k malíčku.
                        // Na ľavej ruke je to smer von (RollOut), na pravej ruke smer dnu (RollIn).
                        type = (byte) (hand1 == LEFT_HAND ? 2 : 1);
                    }
                    // 🔄 REDIRECTS (Otočky a zmeny smeru na jednej ruke)
                    else if ((f1 < f2 && f2 > f3) || (f1 > f2 && f2 < f3)) {
                        // Prsty urobia vlnovku (hore-dole alebo dole-hore).
                        // Podmienka: Všetky tri prsty musia byť jedinečné (žiadne opakovanie prsta).
                        if (f1 != f2 && f2 != f3 && f1 != f3) {
                            type = 3;
                        }
                    } else if (f1 == f2 && f1 == f3) {
                        type = 4;
                        IS_SFT[i][j][k] = true;
                    }

                    // 🚀 ZÁCHRANNÝ KOŠ: Ak trojica slotov (i, j, k) tvorí platný ergonomický pohyb,
                    // "vylisujeme" ich indexy do plochého 1D dopravníkového pásu.
                    // Ak trigram prešiel sitom biomechaniky, zapíšeme ho do 1D flat polí
                    if (type > 0) {
                        TRIPLE_I[VALID_TRIPLES_COUNT] = i;
                        TRIPLE_J[VALID_TRIPLES_COUNT] = j;
                        TRIPLE_K[VALID_TRIPLES_COUNT] = k;
                        TRIPLE_TYPE[VALID_TRIPLES_COUNT] = type;
                        VALID_TRIPLES_COUNT++; // Posunieme počítadlo (zastaví sa presne na 6 750)
                    }
                }
            }
        }
    }
}
