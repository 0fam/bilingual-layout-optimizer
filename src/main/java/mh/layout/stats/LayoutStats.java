package mh.layout.stats;

import mh.layout.topology.Finger;
import mh.layout.topology.KeyboardTopology;

public final class LayoutStats {

    private double totalScore = 0.0;

    // Čisté jednorozmerné polia pripravené na priamy zápis/čítanie
    private final double[] metricPercents = new double[MetricType.values().length];
    private final double[] fingerPercents = new double[Finger.values().length];
    private final double[] keyPercents = new double[KeyboardTopology.KEY_COUNT];

    public LayoutStats() {}

    // --- GETTERY A SETTERY (Čisté priradenie hodnôt bez prepočtov) ---

    public double getScore() { return totalScore; }
    public void setScore(double score) { this.totalScore = score; }

    public double getMetricPercent(MetricType type) { return metricPercents[type.ordinal()]; }
    public void setMetricPercent(MetricType type, double value) { this.metricPercents[type.ordinal()] = value; }

    public double getFingerPercent(int fingerOrdinal) { return fingerPercents[fingerOrdinal]; }
    public void setFingerPercent(int fingerOrdinal, double value) { this.fingerPercents[fingerOrdinal] = value; }

    public double getKeyPercent(int pos) { return keyPercents[pos]; }
    public void setKeyPercent(int pos, double value) { this.keyPercents[pos] = value; }
}
