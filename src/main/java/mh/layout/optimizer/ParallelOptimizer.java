package mh.layout.optimizer;

import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.Weights;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.NGramStats;
import mh.layout.topology.KeyboardTopology;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ParallelOptimizer {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private char[] globalBestMatrix;
    private double globalBestScore = -1.0;

    private final Object lock = new Object();
    private final Object journalLock = new Object();

    private long totalEvaluations = 0;
    private static final String JOURNAL_FILE = "layout_journal.txt";

    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        if (isRunning.get()) return;
        isRunning.set(true);

        double enRatio = 1.0 - skRatio;
        this.globalBestMatrix = Arrays.copyOf(initialMatrix, initialMatrix.length);

        LayoutStats initSk = Evaluator.evaluate(globalBestMatrix, statsSK, weights);
        LayoutStats initEn = Evaluator.evaluate(globalBestMatrix, statsEN, weights);
        this.globalBestScore = (initSk.getScore() * skRatio) + (initEn.getScore() * enRatio);

        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("🔥 Štartujem optimizér na " + cores + " jadrách procesora...");
        System.out.println("📝 Denník (SK >= 80, EN >= 70) zapisuje bez ohľadu na zlepšenie do: " + JOURNAL_FILE);

        for (int i = 0; i < cores; i++) {
            final int threadId = i;
            Thread worker = new Thread(() -> runAnnealingWorker(threadId, initialMatrix, statsSK, statsEN, weights, skRatio));
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
    private void runAnnealingWorker(int threadId, char[] startMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        Random rand = new Random();
        char[] currentMatrix = Arrays.copyOf(startMatrix, startMatrix.length);

        LayoutStats currentSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights);
        LayoutStats currentEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights);

        double enRatio = 1.0 - skRatio;
        double currentScore = (currentSkStats.getScore() * skRatio) + (currentEnStats.getScore() * enRatio);

        // 🌡️ PARAMETRE ŽÍHANIA (Tieto čísla definujú kvalitu vyhľadávania)
        double temperature = 100.0;
        final double coolingRate = 0.00005; // Ako rýchlo klesá teplota (menšie číslo = dôkladnejšie hľadanie)

        long localEvaluations = 0;

        // Bežíme do nekonečna (kým isRunning je true)
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

            // 3. Ohodnoť nový layout bilingválne
            LayoutStats nextSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights);
            LayoutStats nextEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights);

            double skScore = nextSkStats.getScore();
            double enScore = nextEnStats.getScore();
            double nextScore = (skScore * skRatio) + (enScore * enRatio);

            localEvaluations++;

            // 🚀 ULTRA-OPTIMALIZÁCIA: Jeden spoločný heartbeat blok
            if ((localEvaluations & 0x3FFF) == 0) {
                // 1. Živý výpis do konzoly
                System.out.printf("[Jadro %d] Počítam a žijem... Aktuálne skóre SK: %.4f EN: %.4f (Teplota: %.2f)%n",
                        threadId, skScore, enScore, temperature);

                // 2. Bezpečné pripočítanie rýchlosti do globálneho počítadla
                synchronized (lock) {
                    totalEvaluations += 16384;
                }
            }

            // 📝 STRICT ZÁPIS DO DENNÍKA: Bez ohľadu na zlepšenie
            if (skScore >= 80.0 && enScore >= 70.0) {
                appendToJournal(currentMatrix, skScore, enScore, nextScore, threadId, localEvaluations);
            }

            // 4. Rozhodovacia matematika
            if (nextScore > currentScore) {
                currentScore = nextScore;
                checkAndSetGlobalBest(currentMatrix, currentScore, skScore, enScore);
            } else {
                double delta = currentScore - nextScore;
                double acceptanceProbability = Math.exp(-delta / temperature);

                if (rand.nextDouble() < acceptanceProbability) {
                    currentScore = nextScore;
                } else {
                    char undo = currentMatrix[pos1];
                    currentMatrix[pos1] = currentMatrix[pos2];
                    currentMatrix[pos2] = undo;
                }
            }

            // 5. Ochladzovanie systému
            temperature *= (1.0 - coolingRate);
        }
    }

    private void checkAndSetGlobalBest(char[] matrix, double score, double sk, double en) {
        synchronized (lock) {
            if (score > globalBestScore) {
                globalBestScore = score;
                globalBestMatrix = Arrays.copyOf(matrix, matrix.length);
                System.out.printf("🏆 Nový bilingválny rekord! Skóre: %.4f (SK: %.2f | EN: %.2f) [%s]%n",
                        score, sk, en, new String(globalBestMatrix));
            }
        }
    }

    private void appendToJournal(char[] matrix, double sk, double en, double combined, int threadId, long iteration) {
        synchronized (journalLock) {
            try (FileWriter fw = new FileWriter(JOURNAL_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.printf("%s | SK: %.4f | EN: %.4f | COMBINED: %.4f | %s (Jadro %d)%n | %d",
                        new String(matrix), sk, en, combined, timestamp, threadId,  iteration);

            } catch (IOException e) {
                System.err.println("Chyba zápisu do denníka: " + e.getMessage());
            }
        }
    }
}
