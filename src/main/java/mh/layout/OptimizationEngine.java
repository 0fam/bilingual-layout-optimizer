package mh.layout;

import mh.layout.evaluator.Weights;
import mh.layout.optimizer.ParallelOptimizer;
import mh.layout.stats.NGramStats;

public final class OptimizationEngine {

    // 🚀 REFAKTOR: Metóda teraz prijíma už hotové, naplnené štatistiky z Main
    public void run(NGramStats statsSK, NGramStats statsEN, double thresholdSk, double thresholdEn) {
        System.out.println("====================================================");
        System.out.println("       BILINGVÁLNY OPTIMIZÉR KLÁVESNICE TOTEM        ");
        System.out.println("====================================================\n");

        // 1. ŠTARTOVACÍ LAYOUT (30 klávesov, mriežka 3x10)
        String qwertyBase = (
                "q x w h ;  . , l c b" +
                "o a e i y  v s n t m" +
                "g u k j '  f z r d p").replace(" ", "").toUpperCase();
        char[] initialMatrix = qwertyBase.toCharArray();

        // 2. INICIALIZÁCIA LIMITOV
        // Všetky tvoje safe stropy a death gilotíny sa načítajú automaticky z konštruktora
        Weights weights = new Weights();

        // 5. NASTAVENIE BILINGVÁLNEHO POMERU JAZYKOV
        // 0.80 znamená: 80 % dôležitosti má slovenčina, 20 % angličtina
        double skRatio = 0.80;

        // 3. ODPÁLENIE MULTITHREADOVÉHO MOTORA (Dáta idú priamo dnu)
        ParallelOptimizer optimizer = new ParallelOptimizer();
        optimizer.startOptimization(initialMatrix, statsSK, statsEN, weights, skRatio, thresholdSk, thresholdEn);

        // 4. MONITOROVACIA SLUČKA (Tachometer rýchlosti)
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

            System.out.printf("📊 Rýchlosť: %,d layoutov/s | Celkovo preverených: %,d%n", speedPerSecond, totalEvaluations);
        }
    }
}
