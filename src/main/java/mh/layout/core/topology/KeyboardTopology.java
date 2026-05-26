package mh.layout.core.topology;

public final class KeyboardTopology {

    private KeyboardTopology() {}

    /**
     * Mapa indexov klávesnice (3 riadky x 10 stĺpcov = 30 pozícií):
     *  0  1  2  3  4  |  5  6  7  8  9   <- Horný riadok
     * 10 11 12 13 14  | 15 16 17 18 19   <- Domovský riadok (Home Row)
     * 20 21 22 23 24  | 25 26 27 28 29   <- Spodný riadok
     */

    // 0 = Ľavá ruka, 1 = Pravá ruka
    public static final int[] POS_TO_HAND = {
            0, 0, 0, 0, 0,  1, 1, 1, 1, 1,
            0, 0, 0, 0, 0,  1, 1, 1, 1, 1,
            0, 0, 0, 0, 0,  1, 1, 1, 1, 1
    };

    // 0 = Horný, 1 = Stredný (Home), 2 = Spodný riadok
    public static final int[] POS_TO_ROW = {
            0, 0, 0, 0, 0,  0, 0, 0, 0, 0,
            1, 1, 1, 1, 1,  1, 1, 1, 1, 1,
            2, 2, 2, 2, 2,  2, 2, 2, 2, 2
    };

    // ID prstov od 0 do 7 (zľava doprava: ľavý malíček je 0 ... pravý malíček je 7)
    public static final int[] POS_TO_FINGER = {
            // Doplň svoje presné prstové mapovanie pre 30 pozícií
    };

    // Tvoja nová asymetrická váha nepohodlia/natiahnutia pre každú z 30 pozícií
    public static final double[] POS_STRETCH_WEIGHT = {
            // Doplň svoje double hodnoty penalizácií riadkov a stredových stĺpcov
    };
}
