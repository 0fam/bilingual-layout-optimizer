package mh.layout;

import mh.layout.evaluator.Weights;
import mh.layout.optimizer.ParallelOptimizer;
import mh.layout.stats.NGramStats;

public final class OptimizationEngine {

    // 🚀 REFAKTOR: Metóda teraz prijíma už hotové, naplnené štatistiky z Main
    public void run(NGramStats statsSK, NGramStats statsEN) {
        System.out.println("====================================================");
        System.out.println("       BILINGVÁLNY OPTIMIZÉR KLÁVESNICE TOTEM        ");
        System.out.println("====================================================\n");

        // 1. ŠTARTOVACÍ LAYOUT (30 klávesov, mriežka 3x10)
//        String baseLayout = (
//                "q x w h ;  . , l c b" +
//                "o a e i y  v s n t m" +
//                "g u k j '  f z r d p").replace(" ", "").toUpperCase();

//        String baseLayout = (
//                "Z / M V P   W Q F , X" +
//                "S E T K D   L N A O I" +
//                "G ; C B H   J R U . Y").replace(" ", "").toUpperCase();

//        String baseLayout = (
//                "H N T C F  - X ' Y V" +
//                "K R D S B  . A E I L" +
//                "P J M Z G  Q O U , W").replace(" ", "").toUpperCase();

        String baseLayout = ( // to iste co 2 ale vybrane symboly
                "Z - M V P   W Q F , X" +
                "S E T K D   L N A O I" +
                "G ' C B H   J R U . Y").replace(" ", "").toUpperCase();
        char[] initialMatrix = baseLayout.toCharArray();

        // 2. INICIALIZÁCIA LIMITOV
        // Všetky tvoje safe stropy a death gilotíny sa načítajú automaticky z konštruktora
        Weights weights = new Weights();

        // 5. NASTAVENIE BILINGVÁLNEHO POMERU JAZYKOV
        // 0.80 znamená: 80 % dôležitosti má slovenčina, 20 % angličtina
        double skRatio = 0.80;

        // 3. ODPÁLENIE MULTITHREADOVÉHO MOTORA (Dáta idú priamo dnu)
        ParallelOptimizer optimizer = new ParallelOptimizer();
        optimizer.startOptimization(initialMatrix, statsSK, statsEN, weights, skRatio);

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
