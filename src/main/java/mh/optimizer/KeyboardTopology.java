package mh.optimizer;

public class KeyboardTopology {
    // Definujeme 8 prstov (0=Ľ Malíček, 1=Ľ Prstenník, 2=Ľ Prostredník, 3=Ľ Ukazovák, 4=P Ukazovák, 5=P Prostredník...)
    public static final int L_PINKY = 0, L_RING = 1, L_MIDDLE = 2, L_INDEX = 3;
    public static final int R_INDEX = 4, R_MIDDLE = 5, R_RING = 6, R_PINKY = 7;

    // Fyzické mapovanie každej z 30 pozícií na ruku, riadok a prst
    public static final int[] POS_TO_HAND = new int[30];   // 0 = Ľavá, 1 = Pravá
    public static final int[] POS_TO_ROW = new int[30];    // 0 = Top, 1 = Home, 2 = Bottom
    public static final int[] POS_TO_FINGER = new int[30]; // 0 až 7
    // 🔥 NOVINKA: Namiesto booleanu ukladáme presnú váhu nepohodlia pozície
    public static final double[] POS_STRETCH_WEIGHT = new double[30];

    static {
        for (int pos = 0; pos < 30; pos++) {
            int row = pos / 10;
            int col = pos % 10;

            POS_TO_ROW[pos] = row;
            POS_TO_HAND[pos] = (col < 5) ? 0 : 1;

            // Výpočet trestných bodov pre stredové stĺpce (stĺpce 4 a 5)
            if (col == 4 || col == 5) {
                if (row == 0) {
                    POS_STRETCH_WEIGHT[pos] = 2.0;  // 🔥 TOP STRETCH = Absolútna smrť (penalizácia x3)
                } else if (row == 2) {
                    POS_STRETCH_WEIGHT[pos] = 1.0;  // BOTTOM STRETCH = Zlé, ale anatomicky lepšie ako top
                } else {
                    POS_STRETCH_WEIGHT[pos] = 0.1;  // HOME STRETCH = Úplne v pohode, takmer bez trestu
                }
            } else {
                POS_STRETCH_WEIGHT[pos] = 0.0; // Ostatné klávesy nemajú stretch penalizáciu
            }

            // Priradenie prstov podľa stĺpcov
            if (col == 0) POS_TO_FINGER[pos] = L_PINKY;
            else if (col == 1) POS_TO_FINGER[pos] = L_RING;
            else if (col == 2) POS_TO_FINGER[pos] = L_MIDDLE;
            else if (col == 3 || col == 4) POS_TO_FINGER[pos] = L_INDEX; // stĺpec 4 je stretch na ukazováku
            else if (col == 5 || col == 6) POS_TO_FINGER[pos] = R_INDEX; // stĺpec 5 je stretch na ukazováku
            else if (col == 7) POS_TO_FINGER[pos] = R_MIDDLE;
            else if (col == 8) POS_TO_FINGER[pos] = R_RING;
            else if (col == 9) POS_TO_FINGER[pos] = R_PINKY;
        }
    }
}
