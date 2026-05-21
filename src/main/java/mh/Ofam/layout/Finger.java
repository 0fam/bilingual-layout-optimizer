package mh.Ofam.layout;

enum Finger {
    L_PINKY(0), L_RING(1), L_MIDDLE(2), L_INDEX(3), L_INNER(4),
    R_INNER(5), R_INDEX(6), R_MIDDLE(7), R_RING(8), R_PINKY(9);

    final int rank;

    Finger(int col) {
        this.rank = col <= 4 ? col : 9 - col;
    }
}
