package mh.layout.optimizer;

import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.NGramStats;
import mh.layout.topology.KeyboardTopology;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ParallelOptimizer {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Globálne najlepšie výsledky, ktoré zdieľajú všetky vlákna
    private char[] globalBestMatrix;
    private double globalBestScore = -1.0;
    private final Object lock = new Object();

    // Počítadlo celkového počtu otestovaných layoutov (pre tvoju informáciu o rýchlosti)
    private long totalEvaluations = 0;

    public void startOptimization(char[] initialMatrix, NGramStats ngStats, Weights weights) {
        if (isRunning.get()) return;
        isRunning.set(true);

        this.globalBestMatrix = Arrays.copyOf(initialMatrix, initialMatrix.length);
        this.globalBestScore = Evaluator.evaluate(globalBestMatrix, ngStats, weights).getScore();

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("🔥 Štartujem optimizér na " + cores + " jadrách procesora...");

        for (int i = 0; i < cores; i++) {
            final int threadId = i;
            Thread worker = new Thread(() -> runAnnealingWorker(threadId, initialMatrix, ngStats, weights));
            worker.setDaemon(true);
            worker.start();
        }
    }

    public void stop() {
        isRunning.set(false);
    }

    public char[] getBestMatrix() {
        synchronized (lock) {
            return Arrays.copyOf(globalBestMatrix, globalBestMatrix.length);
        }
    }

    public double getBestScore() {
        synchronized (lock) {
            return globalBestScore;
        }
    }

    public long getTotalEvaluations() {
        return totalEvaluations;
    }

    // =========================================================================
    // ⚙️ JADRO ŽÍHANIA: Spúšťa sa samostatne v každom vlákne
    // =========================================================================
    private void runAnnealingWorker(int threadId, char[] startMatrix, NGramStats ngStats, Weights weights) {
        Random rand = new Random();
        char[] currentMatrix = Arrays.copyOf(startMatrix, startMatrix.length);
        LayoutStats currentStats = Evaluator.evaluate(currentMatrix, ngStats, weights);
        double currentScore = currentStats.getScore();

        // 🌡️ PARAMETRE ŽÍHANIA (Tieto čísla definujú kvalitu vyhľadávania)
        double temperature = 100.0;
        final double coolingRate = 0.00005; // Ako rýchlo klesá teplota (menšie číslo = dôkladnejšie hľadanie)

        long localEvaluations = 0;

        while (isRunning.get()) { // && temperature > 0.1) {

            // 🌡️ RE-HEATING: Ak pec vyhasla, zapálime ju znova a hľadáme novú trasu
            if (temperature <= 0.1) {
                temperature = 100.0;
            }

            // 1. Náhodný výber dvoch pozícií na prehodenie (Swapping)
            int pos1 = rand.nextInt(KeyboardTopology.KEY_COUNT);
            int pos2 = rand.nextInt(KeyboardTopology.KEY_COUNT);
            if (pos1 == pos2) continue;

            // 2. Vykonaj swap (krok vpred)
            char temp = currentMatrix[pos1];
            currentMatrix[pos1] = currentMatrix[pos2];
            currentMatrix[pos2] = temp;

            // 3. Ohodnoť nový layout
            LayoutStats nextStats = Evaluator.evaluate(currentMatrix, ngStats, weights);
            double nextScore = nextStats.getScore();
            localEvaluations++;

            // 4. Rozhodovacia matematika (Metropolisovo kritérium)
            // Kedže máme skóre od 0 do 100 (kde 100 je top), chceme skóre maximalizovať!
            if (nextScore > currentScore) {
                // Nový layout je lepší -> jednoznačne prijímame
                currentScore = nextScore;

                // Skontrolujeme, či sme neprekonali celosvetový rekord aplikácie
                checkAndSetGlobalBest(currentMatrix, currentScore);
            } else {
                // Nový layout je horší -> prijmeme ho iba s určitou pravdepodobnosťou (únik z pasce)
                double delta = currentScore - nextScore; // Kladné číslo
                double acceptanceProbability = Math.exp(-delta / temperature);

                if (rand.nextDouble() < acceptanceProbability) {
                    // Prijímame aj horší krok
                    currentScore = nextScore;
                } else {
                    // Odmietame -> vrátime swap späť
                    char undo = currentMatrix[pos1];
                    currentMatrix[pos1] = currentMatrix[pos2];
                    currentMatrix[pos2] = undo;
                }
            }

            // 5. Ochladzovanie systému
            temperature *= (1.0 - coolingRate);

            // Občas pripočítame lokálne výpočty do globálneho počítadla rychlosti
            if (localEvaluations % 10000 == 0) {
                synchronized (lock) {
                    totalEvaluations += 10000;
                }
            }
        }
    }

    private void checkAndSetGlobalBest(char[] matrix, double score) {
        synchronized (lock) {
            if (score > globalBestScore) {
                globalBestScore = score;
                globalBestMatrix = Arrays.copyOf(matrix, matrix.length);
                System.out.printf("🏆 Nový rekord! Skóre: %.4f [%s]%n", score, new String(globalBestMatrix));
            }
        }
    }
}
