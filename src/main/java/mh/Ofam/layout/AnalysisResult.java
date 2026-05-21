package mh.Ofam.layout;

class AnalysisResult {
    String layoutName;
    LayoutStats layoutStats;
    double balancedScore = 0.0;

    AnalysisResult(String layoutName, LayoutStats layoutStats) {
        this.layoutName = layoutName;
        this.layoutStats = layoutStats;
    }

    String getLayoutName() {
        return layoutName;
    }

    double getSfbPct() {
        return layoutStats.totalBigrams == 0 ? 0 : (layoutStats.sfb * 100.0) / layoutStats.totalBigrams;
    }

    double getSfsPct() {
        return layoutStats.totalSkipgrams == 0 ? 0 : (layoutStats.sfs * 100.0) / layoutStats.totalSkipgrams;
    }

    double getInnerStretchPct() {
        return layoutStats.totalMonograms == 0 ? 0 : (layoutStats.innerStretch * 100.0) / layoutStats.totalMonograms;
    }

    double getAlternationPct() {
        return layoutStats.totalBigrams == 0 ? 0 : (layoutStats.handAlternation * 100.0) / layoutStats.totalBigrams;
    }

    double getHomeRowPct() {
        return layoutStats.totalMonograms == 0 ? 0 : (layoutStats.rows[1] * 100.0) / layoutStats.totalMonograms;
    }

    long getLongOneHandedRuns() {
        return layoutStats.longOneHandedRuns;
    }

    // Nové pridané gettery pre nové metriky (predpokladá sa, že sú už implementované v LayoutStats)
    double getScissorsPct() {
        return layoutStats.totalBigrams == 0 ? 0 : (layoutStats.scissors * 100.0) / layoutStats.totalBigrams;
    }

    double getRedirectsPct() {
        return layoutStats.totalTrigrams == 0 ? 0 : (layoutStats.redirects * 100.0) / layoutStats.totalTrigrams;
    }

    double getRollInPct() {
        return layoutStats.totalTrigrams == 0 ? 0 : (layoutStats.inwardRolls * 100.0) / layoutStats.totalTrigrams;
    }

    double getRollOutPct() {
        return layoutStats.totalTrigrams == 0 ? 0 : (layoutStats.outwardRolls * 100.0) / layoutStats.totalTrigrams;
    }

    double getBalancedScore() {
        return balancedScore;
    }
}
