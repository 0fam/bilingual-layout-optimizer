package mh.layout;

public final class Main {
    public static void main(String[] args) {
        // MOŽNOSŤ A: Spustenie optimalizácie (Hľadanie nového šampióna)
//         new OptimizationEngine().run();

        // MOŽNOSŤ B: Standalone analýza a rebríček hotových layoutov z disku
        new LayoutAnalysisEngine().runStandalone("c:\\home\\project-personal\\layout-analyzer\\layout_journal.txt");
    }
}
