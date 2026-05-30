package mh.layout.analyzer;

import mh.layout.stats.LayoutStats;

public record AnalyzedLayout(
        String name,
        char[] matrix,
        LayoutStats skStats,
        LayoutStats enStats,
        double combinedScore
) {
    // 🚀 STATICKÁ TOVÁRENSKÁ METÓDA: Vyhne sa kolízii signatúr a čisto vytvorí nový rekord
    public static AnalyzedLayout instance(String name, char[] matrix, LayoutStats skStats, LayoutStats enStats, double ratioSk) {
        double combinedScore = combineScore(skStats, enStats, ratioSk);
        return new AnalyzedLayout(name, matrix, skStats, enStats, combinedScore);
    }

    // 🚀 STATICKÁ METÓDA PRE OPTIMIZER: Zostáva nezmenená, super rýchla
    public static double combineScore(LayoutStats skStats, LayoutStats enStats, double ratioSk) {
        return enStats == null ? skStats.getScore() : Math.pow(skStats.getScore(), ratioSk) * Math.pow(enStats.getScore(), 1.0 - ratioSk);
    }
}
