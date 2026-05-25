package mh.optimizer;

public class FastLayout {
    public String name;
    public char[] matrix = new char[30];

    // O(1) priame lookup tabuľky pre evaluátor
    public final int[] charToPos = new int[256];
    public final int[] charToFinger = new int[256];
    public final int[] charToHand = new int[256];
    public final int[] charToRow = new int[256];
    public final boolean[] charIsStretch = new boolean[256];

    public FastLayout(String name, String layoutString) {
        this.name = name;
        // Očistíme string od medzier/nových riadkov a naplníme maticu 30 znakmi
        String clean = layoutString.replaceAll("\\s+", "");
        if (clean.length() != 30) {
            throw new IllegalArgumentException("Layout string musí mať presne 30 znakov! Má: " + clean.length());
        }
        this.matrix = clean.toCharArray();
        updateLookupTables();
    }

    // Kľúč k rýchlosti: Kedykoľvek zmutujeme layout, bleskovo prepočítame lookup indexy
    public void updateLookupTables() {
        // Vyčistíme staré dáta (povedzme -1 pre neexistujúce znaky)
        for (int i = 0; i < 256; i++) {
            charToPos[i] = -1;
            charToFinger[i] = -1;
            charToHand[i] = -1;
            charToRow[i] = -1;
            charIsStretch[i] = false;
        }

        // Naplníme tabuľky podľa aktuálnej pozície znakov v matici
        for (int pos = 0; pos < 30; pos++) {
            char ch = matrix[pos];
            if (ch < 256) {
                charToPos[ch] = pos;
                charToFinger[ch] = KeyboardTopology.POS_TO_FINGER[pos];
                charToHand[ch] = KeyboardTopology.POS_TO_HAND[pos];
                charToRow[ch] = KeyboardTopology.POS_TO_ROW[pos];
                charIsStretch[ch] = KeyboardTopology.POS_IS_STRETCH[pos];
            }
        }
    }

    // Rýchly swap pre potreby mutácií v genetickom algoritme
    public void swap(int pos1, int pos2) {
        char temp = matrix[pos1];
        matrix[pos1] = matrix[pos2];
        matrix[pos2] = temp;
        updateLookupTables(); // Okamžitá synchronizácia cache tabuliek
    }

    public FastLayout clone() {
        return new FastLayout(this.name, new String(this.matrix));
    }
}
