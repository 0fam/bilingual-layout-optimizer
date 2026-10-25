package mh.layout;

import mh.layout.config.Configuration;
import mh.layout.io.CorpusProcessor;
import mh.layout.stats.NGramStats;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class Main {
    public static void main(String[] args) {
        YAMLMapper mapper = new YAMLMapper();
//        Configuration config = mapper.readValue("",  Configuration.class);

//        boolean optimize = true;
        boolean optimize = false;

        // 1. Nastavenie ciest na jednom jedinom mieste v aplikácii
        String pathSk = "c:\\home\\project-personal\\bilingual-layout-optimizer-korpusy\\korpus_sk_clean.txt";
        String pathEn = "c:\\home\\project-personal\\bilingual-layout-optimizer-korpusy\\korpus_en.txt";
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
            new OptimizationEngine().run(statsSK, statsEN);
        } else {
            // Analyzátor dostane komplet všetko aj so slovníkmi pre detailný audit
            new LayoutAnalysisEngine().runStandalone(statsSK, statsEN, dictSK, dictEN, List.of(
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts\\10_L7139_J07🦄👑",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts\\06_L6093_J_dotQ",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts\\06_L6093_J_dotQ_sym",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts\\06_L6093_J_dotQ_sym2",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts\\dof5.dof",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts.all",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\src\\main\\resources\\layouts",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\00-totem_rekordy.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\01-journal_stare-slabe-metriky.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\02-journal_83_55.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\03-journal_87_77.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\04-journal_87_81.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\05-journal_80_70_dynamic_minusALT.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\06-journal_SFB_SCISS_EFFORT123-140_FINGER8_GEO80_dyn.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\07-journal_SFB_SCISS_EFFORT123-140_FINGER8_GEO80_dyn.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\08-journal_pokus.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\09-journal_upravene_topology.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer-results\\10-journal_pokusy_s_dobrusovanim.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\11-journal_stretchpenalty.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\12-journal_indexmiddlebalance.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\13-journal_rssfd.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\14-journal_rssfd.txt",
//                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\15-journal_cpsatlike.txt",
//                            "c:\\home\\project-personal\\sub-40-alpha-milp\\17_sub-40-journal.cpsat",
//                            "c:\\home\\project-personal\\sub-40-alpha-milp\\18_sub-40-journal.cpsat",
//                            "c:\\home\\project-personal\\sub-40-bin-pos\\19_sub-40-journal.cpsat",
//                            "c:\\home\\project-personal\\sub-40-two\\20_sub-40-journal.cpsat",
//                            "c:\\home\\project-personal\\sub-40-two\\22_sub-40-journal.cpsat",
//                            "c:\\home\\project-personal\\sub-40-two\\21_sub-40-ASFBdown01_lateral0_ASFBUp04_RSSFB05_malicek_prstenik_non-top15.cpsat",
//                            "c:\\home\\project-personal\\sub-40-two\\21_sub-40-ASFBdown01_lateral0_ASFBUp08_RSSFB1_malicek_prstenik_non-top15.cpsat",
                            "c:\\home\\project-personal\\sub-40-two\\suboj_titanov.cpsat",
                            "c:\\home\\project-personal\\bilingual-layout-optimizer\\layout_journal.txt"
                    ),
                    "c:\\home\\project-personal\\bilingual-layout-optimizer\\leaderboard.txt",
                    "c:\\home\\project-personal\\bilingual-layout-optimizer\\layout_audit.txt"
            );
        }
    }
}
