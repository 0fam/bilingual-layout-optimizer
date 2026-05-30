package mh.layout.analyzer;

import mh.layout.stats.LayoutStats;

public record AnalyzedLayout(
        String name,
        char[] matrix,
        LayoutStats skStats,
        LayoutStats enStats,
        double combinedScore
) {
}
