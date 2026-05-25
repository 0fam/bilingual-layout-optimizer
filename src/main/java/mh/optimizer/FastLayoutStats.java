package mh.optimizer;

public class FastLayoutStats {
    public double eliteScore;
    public double balancedScore;
    public double fingerPainScore;

    // Surové percentá pre finálnu tabuľku
    public double sfbPct, sfsPct, stretchPct, scissorsPct, redirectsPct;
    public double lsbPct, lhbPct, altPct, homeRowPct, rollInPct, rollOutPct, runsPct;

    public double[] fingerLoads = new double[8]; // Záťaž 8 prstov
}
