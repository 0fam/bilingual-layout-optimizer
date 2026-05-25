package mh.Ofam.layout;

public class FastWord {
    public final char[] chars;
    public final long freq;
    public final int len;

    public FastWord(String word, long freq) {
        this.chars = word.toCharArray();
        this.freq = freq;
        this.len = chars.length;
    }
}
