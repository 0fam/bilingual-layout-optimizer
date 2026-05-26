package mh.layout.core.topology;

public final class KeyboardTopology {

    public static final int L_PINKY  = 0, L_RING   = 1, L_MIDDLE = 2, L_INDEX  = 3;
    public static final int R_INDEX  = 4, R_MIDDLE = 5, R_RING   = 6, R_PINKY  = 7;

    public static final int LEFT_HAND = 0, RIGHT_HAND = 1;

    public static final int TOP_ROW = 0, HOME_ROW = 1, BOTTOM_ROW = 2;

    private KeyboardTopology() {}

    /**
     * Mapa indexov klávesnice (3 riadky x 10 stĺpcov = 30 pozícií):
     *  0  1  2  3  4  |  5  6  7  8  9   <- Horný riadok
     * 10 11 12 13 14  | 15 16 17 18 19   <- Domovský riadok (Home Row)
     * 20 21 22 23 24  | 25 26 27 28 29   <- Spodný riadok
     */

    /**
     * Mapa rúk
     * 0 = Ľavá ruka, 1 = Pravá ruka
     */
    public static final int[] POS_TO_HAND = {
            0, 0, 0, 0, 0,  1, 1, 1, 1, 1,
            0, 0, 0, 0, 0,  1, 1, 1, 1, 1,
            0, 0, 0, 0, 0,  1, 1, 1, 1, 1
    };

    /**
     * Map riadkov
     * 0 = Horný, 1 = Stredný (Home), 2 = Spodný riadok
     */
    public static final int[] POS_TO_ROW = {
            0, 0, 0, 0, 0,  0, 0, 0, 0, 0,
            1, 1, 1, 1, 1,  1, 1, 1, 1, 1,
            2, 2, 2, 2, 2,  2, 2, 2, 2, 2
    };

    /**
     * Mapa prstov
     * ID prstov (0 až 7 zľava doprava):
     * 0 = Ľavý malíček (LP)   |  4 = Pravý ukazovák (RI)
     * 1 = Ľavý prstenník (LR) |  5 = Pravý prostredník (RM)
     * 2 = Ľavý prostredník (LM)|  6 = Pravý prstenník (RR)
     * 3 = Ľavý ukazovák (LI)  |  7 = Pravý malíček (RP)
     */
    public static final int[] POS_TO_FINGER = {
            0, 1, 2, 3, 3,   4, 4, 5, 6, 7,  // Horný riadok (ukazováky majú dva stĺpce)
            0, 1, 2, 3, 3,   4, 4, 5, 6, 7,  // Domovský riadok
            0, 1, 2, 3, 3,   4, 4, 5, 6, 7   // Spodný riadok
    };

    /**
     * Statická penalizácia pozícií, váha nepohodlia/natiahnutia (Effort / Stretch Weight)
     * Čím nižšie číslo, tým prirodzenejší a komfortnejší stlač klávesu.
     * Základný domovský riadok (silné prsty) = 1.00 (dokonalý komfort).
     */
    public static final double[] POS_STRETCH_WEIGHT = {
            // HORNÝ RIADKOK: Prirodzené natiahnutie prstov smerom von.
            // Malíček je kratší (1.6), ukazovák a prostredník natiahneš najľahšie (1.3).
            // Indexy 4 a 5 sú vnútorné diagonálne streče (1.80) - stredne ťažké.
            1.60, 1.40, 1.30, 1.30, 1.80,   1.80, 1.30, 1.30, 1.40, 1.60,

            // DOMOVSKÝ RIADOK: Prsty na ňom reálne ležia.
            // Silné prsty = 1.00. Malíček má menší offset (1.15).
            // Indexy 14 a 15 sú bočné streče ukazovákov do stredu klávesnice (1.50).
            1.15, 1.00, 1.00, 1.00, 1.50,   1.50, 1.00, 1.00, 1.00, 1.15,

            // SPODNÝ RIADOK: Krčenie prstov pod seba. Ergonomicky najnepríjemnejší riadok.
            // Prstenník (1.70) a malíček (1.90) trpia najviac, lebo zdieľajú šľachy.
            // Indexy 24 a 25 sú najbrutálnejšie vnútorné spodné diagonály (2.10).
            1.90, 1.70, 1.40, 1.40, 2.10,   2.10, 1.40, 1.40, 1.70, 1.90
    };
}
