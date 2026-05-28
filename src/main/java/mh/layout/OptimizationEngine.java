package mh.layout;

import mh.layout.evaluator.Weights;
import mh.layout.io.CorpusProcessor;
import mh.layout.optimizer.ParallelOptimizer;
import mh.layout.stats.NGramStats;

import java.io.IOException;

public final class OptimizationEngine {

    public void run(double thresholdSk, double thresholdEn) {
        System.out.println("====================================================");
        System.out.println("       BILINGVÁLNY OPTIMIZÉR KLÁVESNICE TOTEM        ");
        System.out.println("====================================================\n");

        // 1. ŠTARTOVACÍ LAYOUT (30 klávesov, mriežka 3x10)
        String qwertyBase = "QWERTYUIOP" +
                "ASDFGHJKL;" +
                "ZXCVBNM,./";
        char[] initialMatrix = qwertyBase.toCharArray();

        // 2. INICIALIZÁCIA SCHRÁNOK PRE ŠTATISTIKY
        NGramStats statsSK = new NGramStats();
        NGramStats statsEN = new NGramStats();

        // 3. NAČÍTANIE KORPUSOV (Bez layoutu, čistá pravda o jazyku)
        System.out.println("📥 Načítavam jazykové korpusy...");
        try {
            CorpusProcessor.processFile("c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus.txt", statsSK);
            System.out.println("✅ Slovenský korpus úspešne spracovaný.");

            CorpusProcessor.processFile("c:\\home\\project-personal\\layout-analyzer\\src\\main\\resources\\korpus_en.txt", statsEN);
            System.out.println("✅ Anglický korpus úspešne spracovaný.\n");
        } catch (IOException e) {
            System.err.println("❌ KRITICKÁ CHYBA: Nepodarilo sa načítať korpusy: " + e.getMessage());
            return;
        }

        // 4. INICIALIZÁCIA LIMITOV
        // Všetky tvoje safe stropy a death gilotíny sa načítajú automaticky z konštruktora
        Weights weights = new Weights();

        // 5. NASTAVENIE BILINGVÁLNEHO POMERU JAZYKOV
        // 0.80 znamená: 80 % dôležitosti má slovenčina, 20 % angličtina
        double skRatio = 0.80;

        // 6. ODPÁLENIE MULTITHREADOVÉHO MOTORA
        ParallelOptimizer optimizer = new ParallelOptimizer();
        optimizer.startOptimization(initialMatrix, statsSK, statsEN, weights, skRatio, thresholdSk, thresholdEn);

        // 7. MONITOROVACIA SLUČKA (Tachometer rýchlosti)
        long lastEvaluations = 0;
        System.out.println("🚀 Optimizér úspešne naštartovaný.");
        System.out.println("📊 Sledujem výkon jadier... (Ukonči pomocou CTRL+C)\n");

        while (true) {
            try {
                Thread.sleep(1000); // Kontrola každú sekundu
            } catch (InterruptedException e) {
                break;
            }

            long totalEvaluations = optimizer.getTotalEvaluations();
            long speedPerSecond = totalEvaluations - lastEvaluations;
            lastEvaluations = totalEvaluations;

            System.out.printf("📊 Rýchlosť: %,d layoutov/s | Celkovo preverených: %,d%n",
                    speedPerSecond, totalEvaluations);
        }
    }
}
