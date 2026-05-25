package mh.optimizer;

public static class CompactNGram {
    public final byte[] seq; // Dynamická sekvencia znakov (dĺžka určuje či je to 4-gram, 5-gram atď.)
    public final long freq;  // Frekvencia výskytu v celom korpuse

    public CompactNGram(byte[] seq, long freq) {
        this.seq = seq;
        this.freq = freq;
    }
}
