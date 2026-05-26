package mh.layout.core.stats;

public final class FastLayoutStats {

    private final double eliteScore;

    // --- TRESTNÉ METRIKY ---
    private final double sfb;
    private final double sfs;
    private final double stretch;
    private final double scissors;
    private final double redirects;

    // --- ANATOMICKÉ UKAZOVATELE ---
    private final double fingerStats;
    private final double lsb;
    private final double lhb;

    // --- ODMENOVÉ METRIKY ---
    private final double homeRow;
    private final double alt;
    private final double rollIn;
    private final double rollOut;
    private final double runs;

    public FastLayoutStats(double eliteScore, double sfb, double sfs, double stretch, double scissors,
                           double redirects, double fingerStats, double lsb, double lhb,
                           double homeRow, double alt, double rollIn, double rollOut, double runs) {
        this.eliteScore = eliteScore;
        this.sfb = sfb;
        this.sfs = sfs;
        this.stretch = stretch;
        this.scissors = scissors;
        this.redirects = redirects;
        this.fingerStats = fingerStats;
        this.lsb = lsb;
        this.lhb = lhb;
        this.homeRow = homeRow;
        this.alt = alt;
        this.rollIn = rollIn;
        this.rollOut = rollOut;
        this.runs = runs;
    }

    // Gettery pre všetky polia...
    public double getEliteScore() { return eliteScore; }
    public double getSfb() { return sfb; }
    public double getSfs() { return sfs; }
    public double getStretch() { return stretch; }
    public double getScissors() { return scissors; }
    public double getRedirects() { return redirects; }
    public double getFingerStats() { return fingerStats; }
    public double getLsb() { return lsb; }
    public double getLhb() { return lhb; }
    public double getHomeRow() { return homeRow; }
    public double getAlt() { return alt; }
    public double getRollIn() { return rollIn; }
    public double getRollOut() { return rollOut; }
    public double getRuns() { return runs; }
}
