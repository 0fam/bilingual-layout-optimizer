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
    private static final String JOURNAL_FILE = "c:\\home\\project-personal\\layout-analyzer\\layout_journal.txt";

    // 🚀 INVARIANTNÉ MENOVATELE: Vypočítané iba raz na začiatku pre brutálny návrat rýchlosti
    private double skMono, skBigram, skSkip, skTri;
    private double enMono, enBigram, enSkip, enTri;

    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio, double thresholdSk, double thresholdEn) {
        if (isRunning.get()) return;
        isRunning.set(true);

        double enRatio = 1.0 - skRatio;
        this.globalBestMatrix = Arrays.copyOf(initialMatrix, initialMatrix.length);

        // =========================================================================
        // ⚡ JEDNORAZOVÝ PREDPREČÍTANÝ SÚČET FREKVENCIÍ PRE CELÝ SET ZNAKOV
        // =========================================================================
        skMono = 0; skBigram = 0; skSkip = 0; skTri = 0;
        enMono = 0; enBigram = 0; enSkip = 0; enTri = 0;

        for (int i = 0; i < 30; i++) {
            skMono += statsSK.getMonogramFreq(initialMatrix[i]);
            enMono += statsEN.getMonogramFreq(initialMatrix[i]);
            for (int j = 0; j < 30; j++) {
                skBigram += statsSK.getBigramFreq(initialMatrix[i], initialMatrix[j]);
                skSkip   += statsSK.getSkipgramFreq(initialMatrix[i], initialMatrix[j]);

                enBigram += statsEN.getBigramFreq(initialMatrix[i], initialMatrix[j]);
                enSkip   += statsEN.getSkipgramFreq(initialMatrix[i], initialMatrix[j]);
                for (int k = 0; k < 30; k++) {
                    skTri += statsSK.getTrigramFreq(initialMatrix[i], initialMatrix[j], initialMatrix[k]);
                    enTri += statsEN.getTrigramFreq(initialMatrix[i], initialMatrix[j], initialMatrix[k]);
                }
            }
        }

        // Prvé inicializačné hodnotenie (Miesto 1)
        LayoutStats initSk = Evaluator.evaluate(globalBestMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
        LayoutStats initEn = Evaluator.evaluate(globalBestMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);
        this.globalBestScore = (initSk.getScore() * skRatio) + (initEn.getScore() * enRatio);

        int cores = Runtime.getRuntime().availableProcessors() / 2;
        System.out.println("🔥 Štartujem optimizér na " + cores + " jadrách procesora...");
        System.out.println("📝 Denník (SK >= " + thresholdSk + ", EN >= " + thresholdEn + ") zapisuje bez ohľadu na zlepšenie do: " + JOURNAL_FILE);

        for (int i = 0; i < cores; i++) {
            final int threadId = i;
            Thread worker = new Thread(() -> runAnnealingWorker(threadId, initialMatrix, statsSK, statsEN, weights, skRatio, thresholdSk, thresholdEn));
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
    private void runAnnealingWorker(int threadId, char[] startMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio, double thresholdSk, double thresholdEn) {
        Random rand = new Random();
        char[] currentMatrix = Arrays.copyOf(startMatrix, startMatrix.length);

        // 🚀 LOKÁLNA PAMÄŤ JADRA: Každé jadro si stráži svoj vlastný lokálny klenot
        char[] localBestMatrix = Arrays.copyOf(startMatrix, startMatrix.length);

        LayoutStats currentSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
        LayoutStats currentEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);

        double enRatio = 1.0 - skRatio;
        double currentScore = (currentSkStats.getScore() * skRatio) + (currentEnStats.getScore() * enRatio);
        double localBestScore = currentScore;

        // 🌡️ OPTIMALIZOVANÉ PARAMETRE (Štartujeme nižšie, chladneme pomalšie)
        double temperature = 5.0; // 5.0 úplne stačí pri tvojej mierke skóre
        final double coolingRate = 0.00001; // Smerom k nule ideme hlbšie a precíznejšie

        long localEvaluations = 0;

        // Bežíme do nekonečna (kým isRunning je true)
        while (isRunning.get()) { // && temperature > 0.1) {

            // 🌡️ INTELIGENTNÝ RE-HEATING (Návrat ku koreňom)
            if (temperature <= 0.05) {
                // Pred zážihom vrátime maticu na lokálne maximum tohto jadra
                currentMatrix = Arrays.copyOf(localBestMatrix, localBestMatrix.length);
                currentScore = localBestScore;
                temperature = 3.0; // Prihrejeme len natoľko, aby sme preskočili najbližšiu stenu
                continue;
            }

            // 1. Náhodný výber dvoch pozícií na prehodenie (Swapping)
            int pos1 = rand.nextInt(KeyboardTopology.KEY_COUNT);
            int pos2 = rand.nextInt(KeyboardTopology.KEY_COUNT);
            if (pos1 == pos2) continue;

            // 2. Vykonaj swap (krok vpred)
            char temp = currentMatrix[pos1];
            currentMatrix[pos1] = currentMatrix[pos2];
            currentMatrix[pos2] = temp;

            // 3. Ohodnoť nový layout bilingválne s bleskovými Early Exits (Miesto 3 v horúcej slučke)
            LayoutStats nextSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
            LayoutStats nextEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);

            double skScore = nextSkStats.getScore();
            double enScore = nextEnStats.getScore();
            double nextScore = (skScore * skRatio) + (enScore * enRatio);

            localEvaluations++;

            // 🚀 ULTRA-OPTIMALIZÁCIA: Splachujeme častejšie (každých 4096 iterácií), aby tachometer nežil v nulách
            if ((localEvaluations & 0x0FFF) == 0) {
                // 1. Živý výpis do konzoly
//                System.out.printf("[Jadro %d] Počítam a žijem... Aktuálne skóre SK: %.4f EN: %.4f (Teplota: %.2f)%n", threadId, skScore, enScore, temperature);

                // 2. Bezpečné pripočítanie rýchlosti do globálneho počítadla
                synchronized (lock) {
                    totalEvaluations += 0x0FFF;
                }
            }

            // 📝 STRICT ZÁPIS DO DENNÍKA: Bez ohľadu na zlepšenie, ak spĺňa elitné bilingválne kritérium
            if (skScore >= thresholdSk && enScore >= thresholdEn) {
                System.out.println("📝 Zápis do denníka: " + formatMatrix(currentMatrix) + " SK: " + String.format("%.4f", skScore) + " | EN: " + String.format("%.4f", enScore) + " | Jadro: " + threadId);
                appendToJournal(currentMatrix, skScore, enScore, nextScore, threadId, localEvaluations);
            }

            // ROZHODOVACIA MATEMATIKA
            if (nextScore > currentScore) {
                currentScore = nextScore;

                // 🚀 Ak sme prepísali lokálny rekord jadra, zapíšeme si ho do pamäte
                if (currentScore > localBestScore) {
                    localBestScore = currentScore;
                    localBestMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
                }

                checkAndSetGlobalBest(currentMatrix, currentScore, skScore, enScore);
            } else {
                double delta = currentScore - nextScore;
                double acceptanceProbability = Math.exp(-delta / temperature);

                if (rand.nextDouble() < acceptanceProbability) {
                    currentScore = nextScore;
                } else {
                    // Krok späť (Undo)
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
                        score, sk, en, formatMatrix(globalBestMatrix));
            }
        }
    }

    private void appendToJournal(char[] matrix, double sk, double en, double combined, int threadId, long iteration) {
        synchronized (journalLock) {
            try (FileWriter fw = new FileWriter(JOURNAL_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                // 🚀 FIX: %n presunuté až na úplný koniec riadku, iterácia uprataná plynule do riadku
                out.printf("%s | SK: %.4f | EN: %.4f | COMBINED: %.4f | %s (Core %d) | Iteration: %d%n",
                        formatMatrix(matrix), sk, en, combined, timestamp, threadId, iteration);

            } catch (IOException e) {
                System.err.println("Chyba zápisu do denníka: " + e.getMessage());
            }
        }
    }

    public static String formatMatrix(char[] matrix) {
        if (matrix == null || matrix.length != 30) {
            return "";
        }

        StringBuilder sb = new StringBuilder(37); // Presná dĺžka stringu vrátane medzier

        // Riadok 1 (0-4 | medzera | 5-9) + 2 medzery na oddelenie riadkov
        sb.append(matrix, 0, 5).append(' ').append(matrix, 5, 5).append("  ");

        // Riadok 2 (10-14 | medzera | 15-19) + 2 medzery na oddelenie riadkov
        sb.append(matrix, 10, 5).append(' ').append(matrix, 15, 5).append("  ");

        // Riadok 3 (20-24 | medzera | 25-29)
        sb.append(matrix, 20, 5).append(' ').append(matrix, 25, 5);

        return sb.toString();
    }

}
