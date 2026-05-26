package mh.layout.core.stats;

import lombok.Getter;

@Getter
public final class LayoutStats {

    // Gettery pre všetky polia...
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

    public LayoutStats(double eliteScore, double sfb, double sfs, double stretch, double scissors,
                       double redirects, double fingerStats, double lsb, double lhb,
                       double homeRow, double alt, double rollIn, double rollOut) {
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
    }

}
