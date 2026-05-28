package mh.layout;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        boolean optimize = true;

        if (optimize) {
            // MOŽNOSŤ A: Spustenie optimalizácie (Hľadanie nového šampióna)
            new OptimizationEngine().run();
        } else {
            // MOŽNOSŤ B: Standalone analýza a rebríček hotových layoutov z disku
            new LayoutAnalysisEngine().runStandalone(List.of(
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal.txt"
//                "c:\\home\\project-personal\\layout-analyzer\\totem_rekordy.txt",
//                "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts"
                    ),
                    "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus.txt",
                    "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus_en.txt",
//                null,
                    "c:\\home\\project-personal\\layout-analyzer\\leaderboard.txt",
//                "c:\\home\\project-personal\\layout-analyzer\\layout_audit.txt"
                    null
            );
        }
    }
}
