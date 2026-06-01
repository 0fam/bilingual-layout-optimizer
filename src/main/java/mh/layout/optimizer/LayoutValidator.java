package mh.layout.optimizer;

import mh.layout.topology.KeyboardTopology;
import static mh.layout.topology.KeyboardTopology.KEY_COUNT;

public final class LayoutValidator {

    public static boolean isHumanFriendly(char[] matrix) {
        int row = 1;
        int offset = row * 10;

        for (int col = 1; col < 9; col++) {
            if (col == 4 || col == 5) continue; // Stredné stretch stĺpce nechávame na voľný experiment

            char ch = matrix[offset + col];

            // ZÁKAZ SYMBOLOV: Na hlavných 8 klávesoch domovského riadku musí byť iba čistá abeceda
            if (ch < 'A' || ch > 'Z') return false;

            // ⚡ EARLY PRUNING: Vyhadzujeme len absolútne dno korpusu (Q, W, X).
            if (ch == 'Q' || ch == 'W' || ch == 'X') return false;
        }

        return true;
    }

}
