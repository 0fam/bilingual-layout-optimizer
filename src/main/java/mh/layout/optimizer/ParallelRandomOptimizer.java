package mh.layout.optimizer;

import mh.layout.analyzer.AnalyzedLayout;
import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.NGramsMultipliers;
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

public final class ParallelRandomOptimizer {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public volatile double dynamicThreshold = 0.0;

    private char[] globalBestMatrix;
    private double globalBestScore = -1.0;

    private final Object lock = new Object();

    private long totalEvaluations = 0;

    private NGramsMultipliers skX;
    private NGramsMultipliers enX;

    // 🚀 INVARIANTNÉ MENOVATELE: Vypočítané iba raz na začiatku pre brutálny návrat rýchlosti
    private double skMonoX, skBiX, skSkipX, skTriX;
    private double enMonoX, enBiX, enSkipX, enTriX;

    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        if (isRunning.get()) return;
        isRunning.set(true);

        skX = NGramsMultipliers.compute(initialMatrix, statsSK);
        enX = NGramsMultipliers.compute(initialMatrix, statsEN);
        skMonoX = skX.monoX();
        skBiX = skX.biX();
        skSkipX = skX.skipX();
        skTriX = skX.triX();
        enMonoX = enX.monoX();
        enBiX = enX.biX();
        enSkipX = enX.skipX();
        enTriX = enX.triX();

        double enRatio = 1.0 - skRatio;
        // Prvé inicializačné hodnotenie (Miesto 1)
        this.globalBestMatrix = Arrays.copyOf(initialMatrix, initialMatrix.length);
        LayoutStats initSk = Evaluator.evaluate(globalBestMatrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
        LayoutStats initEn = Evaluator.evaluate(globalBestMatrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);
        this.globalBestScore = (initSk.getScore() * skRatio) + (initEn.getScore() * enRatio);
        this.dynamicThreshold = this.globalBestScore - 0.3;

        int cores = Runtime.getRuntime().availableProcessors() / 2;
        System.out.println("🔥 Štartujem optimizér na " + cores + " jadrách procesora...");
        System.out.println("📝 Pociatocny threshold na zapis do dennika " + LayoutJournal.JOURNAL_FILE + " je " + dynamicThreshold);

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

        // 🚀 LOKÁLNA PAMÄŤ JADRA: Každé jadro si stráži svoj vlastný lokálny klenot
        char[] localBestMatrix = Arrays.copyOf(startMatrix, startMatrix.length);

        LayoutStats currentSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
        LayoutStats currentEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);

        double enRatio = 1.0 - skRatio;
        double currentScore = (currentSkStats.getScore() * skRatio) + (currentEnStats.getScore() * enRatio);
        double localBestScore = currentScore;

        // 🏎️ EPOCHOVÁ PAMÄŤ: Stráži najlepší stav dosiahnutý čisto v tomto jednom behu od reheatingu po ochladenie
        char[] runBestMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
        double runBestScore = currentScore;

        // 🌡️ OPTIMALIZOVANÉ PARAMETRE (Štartujeme nižšie, chladneme pomalšie)
        double temperature = 3.0; // 5.0 rychle prehladavanie, 3 stredne, 1.5 jemne prehladavanie okolo riesenia
        final double coolingRate = 0.000005; // 0.00001 bezne chladenie, 0,000005 pomale brusenie najdeneho riesenia

        long localEvaluations = 0;

        // Bežíme do nekonečna (kým isRunning je true)
        while (isRunning.get()) { // && temperature > 0.1) {

            // 🌡️ INTELIGENTNÝ RE-HEATING (Návrat ku koreňom)
            if (temperature <= 0.05) {
                // =========================================================================
                // 💎 DETERMINISTICKÝ FINÁLNY VYBRUS: 2-opt All-Pairs Greedy Hill Climbing TOHTO konkrétneho behu
                // =========================================================================
                boolean foundImprovement;
                do {
                    foundImprovement = false;
                    for (int i = 0; i < 29; i++) {
                        for (int j = i + 1; j < 30; j++) {
                            // Skús atomicky prehodiť akékoľvek dva klávesy v lokálnom maxime
                            char sweepTemp = runBestMatrix[i];
                            runBestMatrix[i] = runBestMatrix[j];
                            runBestMatrix[j] = sweepTemp;

                            if (isHumanFriendly(runBestMatrix)) {
                                LayoutStats sweepSk = Evaluator.evaluate(runBestMatrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
                                LayoutStats sweepEn = Evaluator.evaluate(runBestMatrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);
                                double sweepScore = (sweepSk.getScore() * skRatio) + (sweepEn.getScore() * enRatio);

                                // Ak sme našli čisté zlepšenie hoci len o miliontinu bodu, ukladáme a reštartujeme sken
                                if (sweepScore > runBestScore) {
                                    runBestScore = sweepScore;
                                    foundImprovement = true;
                                    checkAndSetGlobalBest(runBestMatrix, runBestScore, sweepSk.getScore(), sweepEn.getScore());
                                    break;
                                }
                            }

                            // Ak výmena nepomohla, vráť krok späť
                            char sweepUndo = runBestMatrix[i];
                            runBestMatrix[i] = runBestMatrix[j];
                            runBestMatrix[j] = sweepUndo;
                        }
                        if (foundImprovement) break;
                    }
                } while (foundImprovement);

                // Ak po vybrúsení tento beh prekonal doterajší historický rekord jadra, prepíšeme ho
                if (runBestScore > localBestScore) {
                    localBestScore = runBestScore;
                    localBestMatrix = Arrays.copyOf(runBestMatrix, runBestMatrix.length);
                }

                // Pred zážihom resetujeme behové premenné na aktuálne absolútne maximum tohto jadra
                currentMatrix = Arrays.copyOf(localBestMatrix, localBestMatrix.length);
                currentScore = localBestScore;

                runBestMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
                runBestScore = currentScore;

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

            localEvaluations++;

            // 🚀 ULTRA-OPTIMALIZÁCIA: Splachujeme častejšie (každých 4096 iterácií), aby tachometer nežil v nulách
            if ((localEvaluations & 0x0FFF) == 0) {
                // Bezpečné pripočítanie rýchlosti do globálneho počítadla
                synchronized (lock) {
                    totalEvaluations += 0x0FFF;
                }
            }

            // 🚀 BLESKOVÉ FILTROVANIE: Ak to nie je kognitívne friendly, okamžite to vráť späť
            if (!isHumanFriendly(currentMatrix)) {
                char undo = currentMatrix[pos1];
                currentMatrix[pos1] = currentMatrix[pos2];
                currentMatrix[pos2] = undo;
                continue; // Ideme na ďalšiu iteráciu, n-gramový Evaluator vôbec nespúšťame
            }

            // 3. Ohodnoť nový layout bilingválne s bleskovými Early Exits (Miesto 3 v horúcej slučke)
            LayoutStats nextSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
            LayoutStats nextEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);

            double skScore = nextSkStats.getScore();
            double enScore = nextEnStats.getScore();
            double nextScore = AnalyzedLayout.combineScore(nextSkStats, nextEnStats, skRatio);

            if ((localEvaluations & 0x0FFFF) == 0) {
                // Živý výpis do konzoly
                System.out.printf("[Jadro %d] Počítam a žijem... Aktuálne skóre SK: %.4f EN: %.4f (Teplota: %.2f)%n", threadId, skScore, enScore, temperature);
            }

            // 📝 STRICT ZÁPIS DO DENNÍKA: Bez ohľadu na zlepšenie, ak spĺňa elitné bilingválne kritérium
            if (nextScore >= dynamicThreshold) {
                System.out.println("📝 Zápis do denníka: " + LayoutJournal.format(currentMatrix) + " SK: " + String.format("%.4f", skScore) + " | EN: " + String.format("%.4f", enScore) + " | Jadro: " + threadId);
                LayoutJournal.append(currentMatrix, skScore, enScore, nextScore, threadId, localEvaluations);
            }

            // ROZHODOVACIA MATEMATIKA
            if (nextScore > currentScore) {
                currentScore = nextScore;

                // Ak sme prepísali rekord TOHTO konkrétneho behu, zapíšeme si ho
                if (currentScore > runBestScore) {
                    runBestScore = currentScore;
                    runBestMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
                }

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

//    // 💎 EXHAUSTIVE BACKTRACKING FINÁLNY VYBRUS (Preskúma úplne všetko, čo stúpa nahor)
//    private void executeExhaustiveExploration(char[] matrix, double currentScore, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio, double enRatio) {
//
//        // Skenujeme všetkých 435 unikátnych dvojíc na tejto úrovni stromu
//        for (int i = 0; i < 29; i++) {
//            for (int j = i + 1; j < 30; j++) {
//
//                // Vykonaj swap (krok do hĺbky vetvy)
//                char temp = matrix[i];
//                matrix[i] = matrix[j];
//                matrix[j] = temp;
//
//                if (isHumanFriendly(matrix)) {
//                    LayoutStats sweepSk = Evaluator.evaluate(matrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
//                    LayoutStats sweepEn = Evaluator.evaluate(matrix, statsEN, weights, enMono, enBigram, enSkip, enTri);
//                    double nextScore = (sweepSk.getScore() * skRatio) + (sweepEn.getScore() * enRatio);
//
//                    // 🔥 AK JE TO HOCIJAKÉ ZLEPŠENIE, IDEME REKURZÍVNE DO HLĹBKY TEJTO VETVY!
//                    if (nextScore > currentScore) {
//
//                        // Ak táto vetva prepísala celkový rekord tohto behu, uzamkneme ju
//                        if (nextScore > runBestScore) {
//                            runBestScore = nextScore;
//                            runBestMatrix = Arrays.copyOf(matrix, matrix.length);
//                            checkAndSetGlobalBest(runBestMatrix, runBestScore, sweepSk.getScore(), sweepEn.getScore());
//                        }
//
//                        // Rekurzívne preskúmame, kam až táto konkrétna vetva vedie
//                        executeExhaustiveExploration(matrix, nextScore, statsSK, statsEN, weights, skRatio, enRatio);
//                    }
//                }
//
//                // Krok späť (Rollback) – uvoľníme maticu pre otestovanie ďalšieho swapu na tejto úrovni
//                char undo = matrix[i];
//                matrix[i] = matrix[j];
//                matrix[j] = undo;
//            }
//        }
//    }

    // Na začiatku aplikácie nastav: globalBestScore = 0.0 a dynamicThreshold = 0.0
    public void checkAndSetGlobalBest(char[] matrix, double score, double sk, double en) {
        synchronized (lock) {
            if (score > globalBestScore) {
                globalBestScore = score;
                globalBestMatrix = Arrays.copyOf(matrix, matrix.length);

                // 🚀 DYNAMICKÝ REFAKTOR: Latku pre "zaujímavé" layouty odvodíme od celkového skóre.
                // Povolíme zápis layoutom, ktoré za absolútnym kráľom zaostávajú maximálne o 0.3 bodu.
                this.dynamicThreshold = score - 0.3;

                System.out.printf("%n🏆 NOVÝ GLOBÁLNY REKORD! Skóre: %.4f (SK: %.2f | EN: %.2f) -> Latka pre zápis posunutá na celkové skóre: %.4f%n",
                        score, sk, en, this.dynamicThreshold);
            }
        }
    }

    public static boolean isHumanFriendly(char[] matrix) {
        int row = 1;
        int offset = row * 10;

        for (int col = 1; col < 9; col++) {
            if (col == 4 || col == 5) continue; // Stredné stretch stĺpce nechávame na voľný experiment

            char ch = matrix[offset + col];

            // ZÁKAZ SYMBOLOV: Na hlavných 8 klávesoch domovského riadku musí byť iba čistá abeceda
            if (ch < 'A' || ch > 'Z') return false;

            // ⚡ EARLY PRUNING: Vyhadzujeme len absolútne dno korpusu (Q, W, X).
            if (ch == 'Q' || ch == 'W' || ch == 'X') return false;
        }

        return true;
    }

}
