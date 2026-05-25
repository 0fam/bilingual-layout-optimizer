package mh.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LayoutOptimizerMultiThread {

    public void runOptimization(Map<String, Long> skRawWords, Map<String, Long> enRawWords, String initialLayoutStr) {
        // 1. JEDNORAZOVÉ PREDSPRACOVANIE (Beží na jednom vlákne pred štartom)
        NGramStats skStats = CorpusProcessor.process(skRawWords, 5);
        NGramStats enStats = CorpusProcessor.process(enRawWords, 5);
        Weights weights = Weights.builder().build();

        // Zistíme počet jadier procesora (u teba 8)
        int threadsCount = Runtime.getRuntime().availableProcessors();
        System.out.println("\n🔥 DETEKOVANÉ PROCESOROVÉ JADRÁ: " + threadsCount);
        System.out.println("Spúšťam paralelné hľadanie na všetkých jadrách naraz...");

        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        List<Future<OptimizationResult>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // 2. ODPAĽUJEME SAMOSTATNÉ WORKERY PRE KAŽDÉ JADRO
        for (int t = 0; t < threadsCount; t++) {
            final int workerId = t;
            futures.add(executor.submit(() -> {
                Random random = new Random();

                // Každé vlákno začne s východiskovým layoutom
                FastLayout currentLayout = new FastLayout("Worker-" + workerId, initialLayoutStr);

                // MALÝ TRIK: Prvých pár sekúnd rozhádžeme klávesy náhodne,
                // aby každé jadro hľadalo v úplne inej časti matematického vesmíru
                if (workerId > 0) {
                    for (int s = 0; s < 50; s++) {
                        currentLayout.swap(random.nextInt(30), random.nextInt(30));
                    }
                }

                FastLayoutStats skS = FastEvaluator.evaluate(currentLayout, skStats, weights);
                FastLayoutStats enS = FastEvaluator.evaluate(currentLayout, enStats, weights);
                double bestScore = (0.8 * skS.eliteScore) + (0.2 * enS.eliteScore);

                long iterations = 500_000;
                for (long i = 0; i < iterations; i++) {
                    int pos1 = random.nextInt(30);
                    int pos2 = random.nextInt(30);
                    while (pos1 == pos2) pos2 = random.nextInt(30);

                    currentLayout.swap(pos1, pos2);

                    FastLayoutStats newSk = FastEvaluator.evaluate(currentLayout, skStats, weights);
                    FastLayoutStats newEn = FastEvaluator.evaluate(currentLayout, enStats, weights);
                    double newGlobalScore = (0.8 * newSk.eliteScore) + (0.2 * newEn.eliteScore);

                    if (newGlobalScore > bestScore) {
                        bestScore = newGlobalScore;
                    } else {
                        currentLayout.swap(pos1, pos2); // rollback
                    }
                }
                return new OptimizationResult(currentLayout.matrix, bestScore);
            }));
        }

        // 3. ZBER VÝSLEDKOV Z JADIER
        char[] absoluteBestMatrix = null;
        double absoluteBestScore = -1.0;

        try {
            for (Future<OptimizationResult> future : futures) {
                OptimizationResult res = future.get(); // Čaká na dokončenie konkrétneho jadra
                System.out.printf("Jadro dokončilo prácu. Najlepšie nájdené skóre na tomto jadre: %.4f\n", res.score);
                if (res.score > absoluteBestScore) {
                    absoluteBestScore = res.score;
                    absoluteBestMatrix = res.matrix;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        long endTime = System.currentTimeMillis();
        double totalTimeSec = (endTime - startTime) / 1000.0;

        // 4. VYPÍSANIE ABSOLÚTNEHO VÍŤAZA VŠETKÝCH JADIER
        System.out.println("\n==================================================");
        System.out.println("🔥 PARALELNÁ OPTIMALIZÁCIA DOKONČENÁ 🔥");
        System.out.printf("Celkový čas na všetkých jadrách: %.2f sekúnd\n", totalTimeSec);
        System.out.printf("Celkovo spracovaných: %d iterácií\n", (threadsCount * 500_000));
        System.out.printf("Ultimátne kombinované skóre: %.4f\n", absoluteBestScore);
        System.out.println("==================================================");

        printMatrixBeautifully(absoluteBestMatrix);
    }

    private void printMatrixBeautifully(char[] matrix) {
        System.out.println("\nABSÓLUTNY VÍŤAZ PRE TVOJ TOTEM:\n");
        for (int i = 0; i < 30; i++) {
            System.out.print(matrix[i] + "  ");
            if (i == 4 || i == 14 || i == 24) System.out.print(" |  ");
            if ((i + 1) % 10 == 0) System.out.println();
        }
        System.out.println();
    }

    // Malá interná pomocná štruktúra na prenos výsledkov z jadier
    private static class OptimizationResult {
        final char[] matrix;
        final double score;

        OptimizationResult(char[] matrix, double score) {
            this.matrix = matrix;
            this.score = score;
        }
    }
}
