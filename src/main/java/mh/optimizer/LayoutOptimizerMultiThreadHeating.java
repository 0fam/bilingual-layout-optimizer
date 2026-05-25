package mh.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LayoutOptimizerMultiThreadHeating {

    private final Object globalLock = new Object();
    private double globalBestScore = -1.0;

    public void runOptimization(Map<String, Long> skRawWords, Map<String, Long> enRawWords, String initialLayoutStr, long iterations, int maxN) {
        // 1. JEDNORAZOVÉ PREDSPRACOVANIE
        NGramStats skStats = CorpusProcessor.process(skRawWords, maxN);
//        NGramStats enStats = CorpusProcessor.process(enRawWords, maxN);
        Weights weights = Weights.builder().build();

        // Zistíme počet jadier procesora (u teba 8)
        int threadsCount = 4; // Runtime.getRuntime().availableProcessors() / 2;
        if (threadsCount < 1) threadsCount = 1;

        // NASTAVENIE POČIATOČNEJ LATKY: Načítame východiskový layout, nech hneď od štartu filtrujeme balast
        FastLayout startLayout = new FastLayout("StartCheck", initialLayoutStr);
        FastLayoutStats startSk = FastEvaluator.evaluate(startLayout, skStats, weights);
        this.globalBestScore = startSk.eliteScore;

        System.out.println("\n🔥 DETEKOVANÉ PROCESOROVÉ JADRÁ (Fyzické): " + threadsCount);
        System.out.println("Spúšťam paralelné hľadanie na všetkých jadrách naraz...");

        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        List<Future<OptimizationResult>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // 2. ODPAĽUJEME SAMOSTATNÉ WORKERY PRE KAŽDÉ JADRO
        for (int t = 0; t < threadsCount; t++) {
            final int workerId = t;
            futures.add(executor.submit(() -> {
                Random random = new Random();
                String workerName = "Jadro-" + workerId;

                // Každé vlákno začne s východiskovým layoutom
                FastLayout currentLayout = new FastLayout(workerName, initialLayoutStr);

                // MALÝ TRIK: Prvých pár sekúnd rozhádžeme klávesy náhodne,
                // aby každé jadro hľadalo v úplne inej časti matematického vesmíru
                if (workerId > 0) {
                    for (int s = 0; s < 50; s++) {
                        currentLayout.swap(random.nextInt(30), random.nextInt(30));
                    }
                }

                FastLayoutStats skS = FastEvaluator.evaluate(currentLayout, skStats, weights);
//                FastLayoutStats enS = FastEvaluator.evaluate(currentLayout, enStats, weights);
//                double bestScore = (0.8 * skS.eliteScore) + (0.2 * enS.eliteScore);
                double bestScore = skS.eliteScore;

                // Sledovanie času štartu tohto konkrétneho jadra pre presný prepočet rýchlosti
                long workerStartTime = System.currentTimeMillis();

                for (long i = 0; i < iterations; i++) {

                    // 🔥 NOVINKA: Priebežný živý odhad rýchlosti pre každé jadro osobitne na 10 000 krokov
                    if (i % 10_000 == 0 && i > 0) {
                        long currentNow = System.currentTimeMillis();
                        double elapsedSec = (currentNow - workerStartTime) / 1000.0;
                        double currentIps = elapsedSec > 0 ? (i / elapsedSec) : 0;
                        System.out.printf("   [%s] Spracované: %6d iterácií | Živá rýchlosť: %.0f it/s (Max: %.4f)\n",
                                workerName, i, currentIps, bestScore);
                    }

                    int pos1 = random.nextInt(30);
                    int pos2 = random.nextInt(30);
                    while (pos1 == pos2) pos2 = random.nextInt(30);

                    // MUTÁCIA: Prehodíme klávesy priamo v layoute
                    currentLayout.swap(pos1, pos2);

                    FastLayoutStats newSk = FastEvaluator.evaluate(currentLayout, skStats, weights);
//                    FastLayoutStats newEn = FastEvaluator.evaluate(currentLayout, enStats, weights);
//                    double newGlobalScore = (0.8 * newSk.eliteScore) + (0.2 * newEn.eliteScore);
                    double newGlobalScore = newSk.eliteScore;

                    if (newGlobalScore > bestScore) {
                        bestScore = newGlobalScore;

                        // Kontrola a nahlásenie absolútneho rekordu
                        char[] matrixCopy = currentLayout.matrix.clone();
                        checkAndReportGlobalImprovement(workerName, matrixCopy, bestScore, i);

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
        System.out.printf("Celkovo spracovaných: %d iterácií\n", (threadsCount * iterations));
        System.out.printf("Ultimátne kombinované skóre: %.4f\n", absoluteBestScore);
        System.out.println("==================================================");

        printMatrixBeautifully(absoluteBestMatrix);

        // 🔥 NOVINKA: Porovnanie s pôvodným stavom, nech okamžite vidíš zmeny
        printLayoutDiff(initialLayoutStr, absoluteBestMatrix);
    }

    private void checkAndReportGlobalImprovement(String workerName, char[] matrix, double score, long iteration) {
        synchronized (globalLock) {
            if (score > globalBestScore) {
                globalBestScore = score;
                System.out.printf("\n🚀 [%s] Iterácia %6d: !!! NOVÝ ABSOLÚTNY REKORD -> %.4f !!!\n  Layout: ",
                        workerName, iteration, score);

                for (int i = 0; i < 30; i++) {
                    System.out.print(matrix[i]);
                    if (i == 9 || i == 19) System.out.print(" / ");
                }
                System.out.println("\n");
            }
        }
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

    // 🔥 NOVINKA: Analýza zmien oproti počiatočnému stringu
    private void printLayoutDiff(String initialStr, char[] finalMatrix) {
        System.out.println("🔄 POROVNANIE ZMIEN OPROTI ŠTARTOVACIEMU LAYOUTU:");
        int changesCount = 0;
        for (int i = 0; i < 30; i++) {
            char oldChar = initialStr.charAt(i);
            char newChar = finalMatrix[i];
            if (oldChar != newChar) {
                System.out.printf("   [Pozícia %2d]  %c  ->  %c\n", i, oldChar, newChar);
                changesCount++;
            }
        }
        if (changesCount == 0) {
            System.out.println("   Layout zostal navlas rovnaký (100% zhodný s počiatočným).");
        } else {
            System.out.printf("   Celkovo sa prehadzovalo: %d klávesov.\n", changesCount);
        }
        System.out.println("==================================================\n");
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
