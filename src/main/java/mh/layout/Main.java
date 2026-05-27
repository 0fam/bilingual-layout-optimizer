package mh.layout;

import mh.layout.analyzer.LayoutAnalyzer;
import mh.layout.stats.Weights;
import mh.layout.io.CorpusProcessor;
import mh.layout.stats.NGramStats;
import mh.layout.optimizer.ParallelOptimizer;

import java.io.IOException;
import java.util.Map;

public final class Main {

    public static void main(String[] args) {
        System.out.println("🚀 ŠTARTUJEM BILINGVÁLNY TOTEM LAYOUT OPTIMIZER 🚀\n");

        // =========================================================================
        // 🗺️ 1. ŠTARTOVACÍ LAYOUT (Už žiadne otravné čiarky a apostrofy!)
        // =========================================================================
        // Jeden string rozdelený na 3 riadky po 10 znakov pre vizuálnu kontrolu.
        // Môžeš tu kedykoľvek prepísať akékoľvek písmeno alebo symbol.
        String initialLayoutStr =
                "QWERTYUIOP" +  // Horný riadok (10 klávesov)
                        "ASDFGHJKL;" +  // Domovský riadok (10 klávesov)
                        "ZXCVBNM,./";   // Spodný riadok (10 klávesov)

        // Blesková konverzia stringu na ploché pole char[]
        char[] initialLayout = initialLayoutStr.toCharArray();

        // =========================================================================
        // 📦 2. PRÍPRAVA DÁTOVÝCH SCHRÁNOK
        // =========================================================================
        NGramStats statsSK = new NGramStats();
        NGramStats statsEN = new NGramStats();

        Map<String, Double> slovakDictionary = null;
        Map<String, Double> englishDictionary = null;

        // =========================================================================
        // 📥 3. DYNAMICKÉ NAČÍTANIE KORPUSOV (Podľa symbolov z layoutu)
        // =========================================================================
        try {
            System.out.println("📖 Načítavam slovenský korpus...");
            slovakDictionary = CorpusProcessor.processFile("data/slovak_corpus.txt", statsSK, initialLayout);
            System.out.println("✅ SK Korpus úspešne spracovaný. Unikátnych kusov slov (>=4): " + slovakDictionary.size());

            System.out.println("📖 Načítavam anglický korpus...");
            englishDictionary = CorpusProcessor.processFile("data/english_corpus.txt", statsEN, initialLayout);
            System.out.println("✅ EN Korpus úspešne spracovaný. Unikátnych kusov slov (>=4): " + englishDictionary.size());

        } catch (IOException e) {
            System.err.println("❌ Chyba pri načítaní korpusov! Skontroluj zložku 'data/'.");
            e.printStackTrace();
            return;
        }

        // =========================================================================
        // ⚖️ 4. NASTAVENIE ERGONOMICKÝCH VÁH
        // =========================================================================
        Weights weights = new Weights();
        weights.setHomeRow(10.0);
        weights.setAlt(5.0);
        weights.setRollIn(3.0);
        weights.setRollOut(1.5);

        weights.setStretch(4.0);
        weights.setFingerLoad(6.0);
        weights.setSfb(12.0);
        weights.setSfs(4.0);
        weights.setScissors(7.0);
        weights.setRedirects(5.0);
        weights.setLsb(3.0);
        weights.setLhb(4.0);

        // =========================================================================
        // 🏎️ 5. SPUSTENIE PARALELNEJ MAŠINY
        // =========================================================================
        ParallelOptimizer optimizer = new ParallelOptimizer();
        double slovakRatio = 0.60; // 60% Váha pre SK / 40% pre EN

        optimizer.startOptimization(initialLayout, statsSK, statsEN, weights, slovakRatio);

        // =========================================================================
        // ⏳ 6. ČASOVÝ LIMIT PRE EVALUÁCIU (10 sekúnd na test)
        // =========================================================================
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            System.err.println("❌ Optimalizácia bola prerušená!");
        }

        // =========================================================================
        // 🛑 7. ZASTAVENIE A ZOBRAZENIE REKORDÉRA
        // =========================================================================
        char[] bestLayout = optimizer.stopAndGetBest();
        System.out.println("\n🛑 Optimalizácia ukončená. Sťahujem dáta z jadier...");

        printLayoutMatrix(bestLayout);

        // =========================================================================
        // 📊 8. SPUSTENIE NOVÉHO DIGITÁLNEHO RÖNTGENU (LayoutAnalyzer)
        // =========================================================================
        System.out.println("\n🔍 Generujem kompletný biomechanický audit a rebríčky slov...");

        // 1. Spustíme hĺbkovú analýzu nad slovníkmi bez diakritiky
        LayoutAnalyzer.AnalysisReport report = LayoutAnalyzer.generateReport(
                bestLayout, statsSK, statsEN, slovakDictionary, englishDictionary, weights, slovakRatio
        );

        // 2. Vytlačíme vygenerovaný report priamo do konzoly pre okamžitú kontrolu
        System.out.println(report.toString());

        // 3. Bezpečne zapíšeme celý report do textového súboru na disk pre históriu
        try {
            String reportPath = "data/audit_report.txt";
            report.saveToFile(reportPath);
            System.out.println("💾 Biomechanický audit bol úspešne uložený do: " + reportPath);
        } catch (IOException e) {
            System.err.println("⚠️ Nepodarilo sa uložiť textový report na disk!");
            e.printStackTrace();
        }
    }

    private static void printLayoutMatrix(char[] matrix) {
        System.out.println("\n👑 ABSOLÚTNY VÍTʼAZNÝ BILINGVÁLNY LAYOUT 👑");
        System.out.println("-----------------------------------------");
        for (int i = 0; i < 30; i++) {
            System.out.print("[" + matrix[i] + "] ");
            if ((i + 1) % 10 == 0) {
                System.out.println();
            } else if ((i + 1) % 5 == 0) {
                System.out.print("   ");
            }
        }
        System.out.println("-----------------------------------------");
    }
}
