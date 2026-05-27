package mh.layout.optimizer;

import mh.layout.evaluator.Evaluator;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.NGramStats;
import mh.layout.topology.KeyboardTopology;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ParallelOptimizer {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService threadPool;

    // Globálny šampión
    private char[] globalBestMatrix;
    private double globalBestScore = -Double.MAX_VALUE;

    private final Object globalLock = new Object();   // Zámok pre hľadanie absolútneho víťaza
    private final Object journalLock = new Object();  // Samostatný zámok pre disk (Záchrana výkonu!)

    private static final String JOURNAL_FILE = "layout_journal.txt";

    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        this.globalBestMatrix = initialMatrix.clone();
        this.running.set(true);

        int cores = Runtime.getRuntime().availableProcessors();
        this.threadPool = Executors.newFixedThreadPool(cores);

        System.out.println("🔥 Štartujem bilingválne Simulované Žíhanie na " + cores + " jadrách...");
        System.out.println("📊 Pomer jazykov: " + (skRatio * 100) + "% SK / " + ((1.0 - skRatio) * 100) + "% EN");
        System.out.println("📝 Denník layoutov (SK > 80, EN > 70) bude priebežne dopĺňaný do: " + JOURNAL_FILE);

        for (int i = 0; i < cores; i++) {
            final int threadId = i;
            threadPool.submit(() -> runWorker(threadId, statsSK, statsEN, weights, skRatio));
        }
    }

    private void runWorker(int threadId, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        Random rand = new Random();
        char[] localMatrix;

        synchronized (globalLock) {
            localMatrix = globalBestMatrix.clone();
        }

        // Východisková evaluácia
        LayoutStats skStats = Evaluator.evaluate(localMatrix, statsSK, weights);
        LayoutStats enStats = Evaluator.evaluate(localMatrix, statsEN, weights);

        double enRatio = 1.0 - skRatio;
        double localBestScore = (skStats.getScore() * skRatio) + (enStats.getScore() * enRatio);
        double currentScore = localBestScore;

        long iterations = 0;

        // 🌡️ PARAMETRE ŽÍHANIA
        double temperature = 100.0;
        final double coolingRate = 0.00002;

        while (running.get()) {
            iterations++;

            // Re-heating (Ak systém úplne vychladne, zapneme pec znova pre novú trasu)
            if (temperature < 0.1) {
                temperature = 100.0;
            }

            // 1. MUTÁCIA (Využíva tvoj fixný KEY_COUNT)
            int pos1 = rand.nextInt(KeyboardTopology.KEY_COUNT);
            int pos2 = rand.nextInt(KeyboardTopology.KEY_COUNT);
            if (pos1 == pos2) continue;

            // Fyzický swap
            char temp = localMatrix[pos1];
            localMatrix[pos1] = localMatrix[pos2];
            localMatrix[pos2] = temp;

            // 2. BILINGVÁLNA EVALUÁCIA
            LayoutStats nextSk = Evaluator.evaluate(localMatrix, statsSK, weights);
            LayoutStats nextEn = Evaluator.evaluate(localMatrix, statsEN, weights);

            double nextScore = (nextSk.getScore() * skRatio) + (nextEn.getScore() * enRatio);

            // 3. ROZHODNUTIE (Simulované žíhanie)
            boolean accept = false;

            if (nextScore > currentScore) {
                accept = true;
            } else {
                // Matematika úniku z pasce lokálneho minima
                double delta = currentScore - nextScore;
                double acceptanceProbability = Math.exp(-delta / temperature);
                if (rand.nextDouble() < acceptanceProbability) {
                    accept = true;
                }
            }

            if (accept) {
                currentScore = nextScore;

                // Ak mutácia prekonala doterajšie maximum tohto konkrétneho jadra
                if (currentScore > localBestScore) {
                    localBestScore = currentScore;

                    // 🔍 FILTRÁCIA PRE DENNÍK (Tvoja podmienka)
                    double skScore = nextSk.getScore();
                    double enScore = nextEn.getScore();
                    if (skScore >= 80.0 && enScore >= 70.0) {
                        appendToJournal(localMatrix, skScore, enScore, localBestScore, threadId);
                    }

                    // Kontrola celosvetového rekordu aplikácie
                    synchronized (globalLock) {
                        if (currentScore > globalBestScore) {
                            globalBestScore = currentScore;
                            globalBestMatrix = localMatrix.clone();

                            System.out.printf("[Jadro %d] 🎉 NOVÝ GLOBÁLNY REKORD: %.4f (SK: %.2f | EN: %.2f) po %d m. iteráciách\n",
                                    threadId, globalBestScore, skScore, enScore, (iterations / 1_000_000));
                        }
                    }
                }
            } else {
                // 🚀 OPRAVA: Žiadne nebezpečné inline indexácie. Čistý, bezpečný návrat späť.
                char undo = localMatrix[pos1];
                localMatrix[pos1] = localMatrix[pos2];
                localMatrix[pos2] = undo;
            }

            // Ochladzovanie
            temperature *= (1.0 - coolingRate);

            if (iterations % 20_000_000 == 0) {
                System.out.printf("[Jadro %d] Teplota: %.2f | Iterácie: %d miliónov... \n", threadId, temperature, (iterations / 1_000_000));
            }
        }
    }

    /**
     * Bezpečný, synchronizovaný zápis elitného layoutu do denníka (Append mode).
     */
    private void appendToJournal(char[] matrix, double sk, double en, double combined, int threadId) {
        synchronized (journalLock) {
            try (FileWriter fw = new FileWriter(JOURNAL_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HK:mm:ss"));
                String layoutString = new String(matrix);

                // Formát: Layout (30 znakov) | SK skóre | EN skóre | Kombinované | Čas a jadro
                out.printf("%s | SK: %.4f | EN: %.4f | COMBINED: %.4f | %s (Jadro %d)%n",
                        layoutString, sk, en, combined, timestamp, threadId);

            } catch (IOException e) {
                System.err.println("❌ Chyba pri zápise do denníka layoutov: " + e.getMessage());
            }
        }
    }

    public char[] stopAndGetBest() {
        this.running.set(false);
        if (threadPool != null) {
            threadPool.shutdown(); // Správne upratanie vlákien
        }
        synchronized (globalLock) {
            return globalBestMatrix.clone();
        }
    }
}
