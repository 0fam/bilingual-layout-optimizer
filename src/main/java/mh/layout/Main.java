package mh.layout;

import mh.layout.io.CorpusProcessor;
import mh.layout.stats.NGramStats;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
//        boolean optimize = true;
        boolean optimize = false;

        // 1. Nastavenie ciest na jednom jedinom mieste v aplikácii
        String pathSk = "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus.txt";
        String pathEn = "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus_en.txt";
//        String pathEn = null;

        // 2. Inicializácia pamäťových schránok
        NGramStats statsSK = new NGramStats();
        NGramStats statsEN = null;
        Map<String, Double> dictSK = null;
        Map<String, Double> dictEN = null;

        // 3. JEDNORAZOVÉ NAČÍTANIE PRE CELÚ APLIKÁCIU
        System.out.println("📥 Načítavam jazykové korpusy a slovníky slov...");
        try {
            dictSK = CorpusProcessor.processFile(pathSk, statsSK);
            System.out.println("✅ Slovenský korpus úspešne spracovaný.");
            statsSK.printTopMonograms("Slovenčina"); // Tvoj nový skvelý výpis!

            if (pathEn != null) {
                statsEN = new NGramStats();
                dictEN = CorpusProcessor.processFile(pathEn, statsEN);
                System.out.println("✅ Anglický korpus úspešne spracovaný.\n");
                statsEN.printTopMonograms("Angličtina");
            }
        } catch (IOException e) {
            System.err.println("❌ KRITICKÁ CHYBA: Nepodarilo sa načítať korpusy: " + e.getMessage());
            return;
        }

        // 4. ROZCESTNÍK: Už len posúvame hotové dáta (Závislosti) do enginov
        if (optimize) {
            // Optimizéru stačia čisté n-gramové štatistiky, slovníky slov ho nezaujímajú
            new OptimizationEngine().run(statsSK, statsEN, 80, 70);
        } else {
            // Analyzátor dostane komplet všetko aj so slovníkmi pre detailný audit
            new LayoutAnalysisEngine().runStandalone(statsSK, statsEN, dictSK, dictEN, List.of(
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal.stare-slabe-metriky.txt",
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal_80_70_dynamic_minusALT.txt",
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal_83_55.txt",
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal_87_77.txt",
                            "c:\\home\\project-personal\\layout-analyzer\\layout_journal_87_81.txt",
                            "c:\\home\\project-personal\\layout-analyzer\\totem_rekordy.txt",
//                            "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts",
                            "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts.all"
//                            "c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\layouts\\dof5.dof"
                    ),
                    "c:\\home\\project-personal\\layout-analyzer\\leaderboard.txt",
                    "c:\\home\\project-personal\\layout-analyzer\\layout_audit.txt"
            );
        }
    }
}
