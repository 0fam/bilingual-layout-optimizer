package mh.layout;

public record BitKey(long l1, long l2, long l3) {

    // Prevodná tabuľka: ASCII znak -> 5-bitové číslo (0..31)
    private static final byte[] CHAR_TO_BIT = new byte[128];

    static {
        // 1. 'a' .. 'z' namapujeme na hodnoty 0 .. 25
        for (char c = 'a'; c <= 'z'; c++) {
            byte value = (byte) (c - 'a');
            CHAR_TO_BIT[c] = value;                        // Namapuje 'a'
            CHAR_TO_BIT[Character.toUpperCase(c)] = value; // Namapuje 'A' na TÚ ISTÚ hodnotu
        }
        // 2. Špeciálne znaky namapujeme na zvyšné hodnoty 27 .. 31
        CHAR_TO_BIT['.'] = 26;
        CHAR_TO_BIT[','] = 27;
        CHAR_TO_BIT['-'] = 28;
        CHAR_TO_BIT['\''] = 29;
        CHAR_TO_BIT['/'] = 30;
        CHAR_TO_BIT[';'] = 31;
    }

    /**
     * Prevedie 30-znakový String na BitKey zložený z 3 longov.
     */
    public static BitKey fromString(CharSequence s) {
        long l1 = 0;
        long l2 = 0;
        long l3 = 0;

        check(s);

        // Prvých 12 znakov (12 x 5 = 60 bitov) -> do l1
        for (int i = 0; i < 12; i++) {
            l1 = (l1 << 5) | CHAR_TO_BIT[s.charAt(i)];
        }

        // Ďalších 12 znakov (12 x 5 = 60 bitov) -> do l2
        for (int i = 12; i < 24; i++) {
            l2 = (l2 << 5) | CHAR_TO_BIT[s.charAt(i)];
        }

        // Posledných 6 znakov (6 x 5 = 30 bitov) -> do l3
        for (int i = 24; i < 30; i++) {
            l3 = (l3 << 5) | CHAR_TO_BIT[s.charAt(i)];
        }

        return new BitKey(l1, l2, l3);
    }

    /**
     * Konverzia priamo z char[]
     */
    public static BitKey fromCharArray(char[] chars) {
        long l1 = 0;
        long l2 = 0;
        long l3 = 0;

        check(chars);

        // Direct array access: chars[i] je bleskové
        for (int i = 0; i < 12; i++) {
            l1 = (l1 << 5) | CHAR_TO_BIT[chars[i]];
        }
        for (int i = 12; i < 24; i++) {
            l2 = (l2 << 5) | CHAR_TO_BIT[chars[i]];
        }
        for (int i = 24; i < 30; i++) {
            l3 = (l3 << 5) | CHAR_TO_BIT[chars[i]];
        }

        return new BitKey(l1, l2, l3);
    }

    private static void check(char[] chars) {
        for (int i = 0; i < 30; i++) {
            // ZÁCHRANNÁ SIEŤ: Ak znak nie je namapovaný (je 0), vyhodíme chybu
            char c = chars[i];
            // Ochrana pred pádom poľa (diakritika a iné non-ASCII znaky)
            if (c >= 128) {
                throw new IllegalArgumentException("Znak mimo ASCII tabuľky: '" + c + "' na indexe " + i);
            }
            byte bitValue = CHAR_TO_BIT[c];
            if (bitValue == -1) {
                throw new IllegalArgumentException(
                        "Našiel som nenamapovaný znak vo vstupe: '" + chars[i] + "' (ASCII: " + (int)chars[i] + ") na indexe " + i
                );
            }
        }
    }

    private static void check(CharSequence s) {
        for (int i = 0; i < 30; i++) {
            char c = s.charAt(i);
            // Ak je znak mimo ASCII tabuľky (nad 127), rovno zlyháme, inak by padol ArrayIndexOutOfBoundsException
            if (c >= 128) {
                throw new IllegalArgumentException("Znak mimo ASCII tabuľky: '" + c + "' na indexe " + i);
            }
            byte bitValue = CHAR_TO_BIT[c];
            if (bitValue == -1) {
                throw new IllegalArgumentException(
                        "Našiel som nenamapovaný znak vo vstupe: '" + c + "' (ASCII: " + (int)c + ") na indexe " + i
                );
            }
        }
    }
}