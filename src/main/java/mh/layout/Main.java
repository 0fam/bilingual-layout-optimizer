package mh.layout;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        boolean optimize = true;
//        boolean optimize = false;

        if (optimize) {
            // MOŽNOSŤ A: Spustenie optimalizácie (Hľadanie nového šampióna)
            new OptimizationEngine().run(87, 81);
        } else {
            // MOŽNOSŤ B: Standalone analýza a rebríček hotových layoutov z disku
            new LayoutAnalysisEngine().runStandalone(List.of(
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal.stare-slabe-metriky.txt",
                            "c:\\home\\project-personal\\layout-analyzer\\totem_rekordy.txt",
//                            "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts",
//                            "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts.all"
                            "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts\\dof5.dof"
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
