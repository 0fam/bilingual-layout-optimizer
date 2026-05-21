package mh.Ofam.layout;

class KeyInfo {
    int row;
    int col;
    Finger finger;
    char hand;

    KeyInfo(int row, int col) {
        this.row = row;
        this.col = col;
        this.hand = col <= 4 ? 'L' : 'R';
        this.finger = Finger.values()[col];
    }
}
