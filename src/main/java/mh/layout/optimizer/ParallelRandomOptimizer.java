package mh.layout.optimizer;

import mh.layout.analyzer.AnalyzedLayout;
import mh.layout.evaluator.Evaluator;
import mh.layout.evaluator.NGramsMultipliers;
import mh.layout.evaluator.Weights;
import mh.layout.stats.LayoutStats;
import mh.layout.stats.NGramStats;
import mh.layout.topology.KeyboardTopology;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class ParallelRandomOptimizer {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalJournalWrites = new AtomicLong(0);
    private final AtomicLong totalPolishRuns = new AtomicLong(0);
    private final AtomicLong totalPolishImprovements = new AtomicLong(0);
    private final AtomicLong bestScoreRun = new AtomicLong(0);
    private final AtomicInteger maxPolishDepth = new AtomicInteger(0);

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

    // Statické pole 26 fluidných pozícií (stretche 14, 15, 24, 25 sú vynechané)
    private static final int[] FLUID_INDICES = {
            0, 1, 2, 3, 4,   5, 6, 7, 8, 9,
            10,11,12,13,     16,17,18,19,
            20,21,22,23,     26,27,28,29
    };

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

    public long getTotalJournalWrites() {
        return totalJournalWrites.get();
    }

    public long getTotalPolishRuns() { return totalPolishRuns.get(); }

    public long getTotalPolishImprovements() { return totalPolishImprovements.get(); }

    public long getBestScoreRun() {
        return bestScoreRun.get();
    }

    public int getMaxPolishDepth() {
        return maxPolishDepth.get();
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
//            if (temperature <= 0.05) {
            if (temperature <= 0.15) {

//                // 🚀 VOLANIE EXTRAHOVANEJ PROCEDÚRY:
//                runBestScore = executeDeterministicPolish(runBestMatrix, runBestScore, statsSK, statsEN, weights, skRatio, enRatio, threadId, localEvaluations);

//                // 🚀 ŠTART TOTÁLNEJ EXPLÓZIE VETVIECKÝCH STROMOV
//                // Pripravíme schránky pre rekurzívny zber šampiónov z celého stromu
//                double[] bestScoreInRun = new double[]{ runBestScore };
//                char[] bestMatrixInRun = Arrays.copyOf(runBestMatrix, runBestMatrix.length);
//                localEvaluations += executeExhaustivePolish(runBestMatrix, runBestScore, bestMatrixInRun, bestScoreInRun,
//                        statsSK, statsEN, weights, skRatio, 0);
//                // Keď sa rekurzia úplne rozvinie a vráti späť, schránky obsahujú absolútne maximum
//                runBestScore = bestScoreInRun[0];
//                runBestMatrix = bestMatrixInRun;

                // Ak po vybrúsení tento beh prekonal doterajší historický rekord jadra, prepíšeme ho
                if (runBestScore > localBestScore) {
                    localBestScore = runBestScore;
                    localBestMatrix = Arrays.copyOf(runBestMatrix, runBestMatrix.length);
                }

                // Pred zážihom resetujeme behové premenné na aktuálne absolútne maximum tohto jadra
                currentMatrix = Arrays.copyOf(localBestMatrix, localBestMatrix.length);
                currentScore = localBestScore;

                // 🌟 TU JE TÁ MÁGIA: runBestScore nesmie byť zviazaný s localBestScore!
                // Nastavíme ho na -1.0. Prvé stúpanie v novom behu si ho okamžite privlastní.
                runBestScore = -1.0;
                runBestMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);

                temperature = 3.0; // Prihrejeme len natoľko, aby sme preskočili najbližšiu stenu
                continue;
            }

//            // 1. Náhodný výber dvoch pozícií na prehodenie (Swapping)
//            int pos1 = rand.nextInt(KeyboardTopology.KEY_COUNT);
//            int pos2 = rand.nextInt(KeyboardTopology.KEY_COUNT);
//            if (pos1 == pos2) continue;

            // 1b. izomorfné mapovanie bez opakovania (Sampling without replacement)
            int r1 = rand.nextInt(26); // Výber z 26 možností (0 až 25)
            int r2 = rand.nextInt(25); // Výber z 25 možností (0 to 24)
            // 🔥 MAGICKÝ TRIK: Odstráni while slučku a garantuje r1 != r2
            if (r2 >= r1) {
                r2++;
            }
            // Mapovanie na reálnu topológiu klávesnice
            int pos1 = FLUID_INDICES[r1];
            int pos2 = FLUID_INDICES[r2];

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

//            // 🚀 BLESKOVÉ FILTROVANIE: Ak to nie je kognitívne friendly, okamžite to vráť späť
//            if (!LayoutValidator.isHumanFriendly(currentMatrix)) {
//                char undo = currentMatrix[pos1];
//                currentMatrix[pos1] = currentMatrix[pos2];
//                currentMatrix[pos2] = undo;
//                continue; // Ideme na ďalšiu iteráciu, n-gramový Evaluator vôbec nespúšťame
//            }

            // 3. Ohodnoť nový layout bilingválne s bleskovými Early Exits (Miesto 3 v horúcej slučke)
            LayoutStats nextSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
            LayoutStats nextEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);

            double skScore = nextSkStats.getScore();
            double enScore = nextEnStats.getScore();
            double nextScore = AnalyzedLayout.combineScore(nextSkStats, nextEnStats, skRatio);

//            if ((localEvaluations & 0x0FFFF) == 0) {
                // Živý výpis do konzoly
//                System.out.printf("[Jadro %d] Počítam a žijem... Aktuálne skóre SK: %.4f EN: %.4f (Teplota: %.2f)%n", threadId, skScore, enScore, temperature);
//            }

            // 📝 STRICT ZÁPIS DO DENNÍKA: Bez ohľadu na zlepšenie, ak spĺňa elitné bilingválne kritérium
            if (nextScore >= dynamicThreshold) {
//                System.out.println("📝 Zápis do denníka: " + LayoutJournal.format(currentMatrix) + " SK: " + String.format("%.4f", skScore) + " | EN: " + String.format("%.4f", enScore) + " | Jadro: " + threadId);
                totalJournalWrites.incrementAndGet(); // 🚀 TU: Zapíšeme si každú jednu požiadavku o zápis
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

                checkAndSetGlobalBest(currentMatrix, currentScore, skScore, enScore, "ŽÍHANIE");
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

    private void adToTotalEvaluations(long howMuch) {
        synchronized (lock) {
            totalEvaluations += howMuch;
        }
    }

    // =========================================================================
    // 💎 DETERMINISTICKÝ FINÁLNY VYBRUS: 2-opt All-Pairs Greedy Hill Climbing
    // =========================================================================
    private double executeDeterministicPolish(char[] runBestMatrix, double runBestScore,
                                              NGramStats statsSK, NGramStats statsEN,
                                              Weights weights, double skRatio, double enRatio,
                                              int threadId, long localEvaluations) {
        totalPolishRuns.incrementAndGet(); // 🚀 TU: Započítame, že sa výbrus spustil

        boolean foundImprovement;
        do {
            foundImprovement = false;
            for (int i = 0; i < 29; i++) {
                for (int j = i + 1; j < 30; j++) {
                    // Skús atomicky prehodiť akékoľvek dva klávesy
                    char sweepTemp = runBestMatrix[i];
                    runBestMatrix[i] = runBestMatrix[j];
                    runBestMatrix[j] = sweepTemp;

                    if (LayoutValidator.isHumanFriendly(runBestMatrix)) {
                        LayoutStats sweepSk = Evaluator.evaluate(runBestMatrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
                        LayoutStats sweepEn = Evaluator.evaluate(runBestMatrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);
                        double sweepScore = (sweepSk.getScore() * skRatio) + (sweepEn.getScore() * enRatio);

                        // Ak sme našli čisté zlepšenie, ukladáme a reštartujeme sken
                        if (sweepScore > runBestScore) {
                            runBestScore = sweepScore;
                            foundImprovement = true;
                            totalPolishImprovements.incrementAndGet(); // 🚀 TU: Výbrus práve reálne našiel lepšie skóre!
                            checkAndSetGlobalBest(runBestMatrix, runBestScore, sweepSk.getScore(), sweepEn.getScore(), "VÝBRUS");
                            // 🚀 NOVÝ TICHÝ ZÁPIS DO DENNÍKA AJ POČAS VÝBRUSU:
                            if (sweepScore >= dynamicThreshold) {
                                totalJournalWrites.incrementAndGet(); // Započítame zápis do štatistiky disku
                                LayoutJournal.append(runBestMatrix, sweepSk.getScore(), sweepEn.getScore(), sweepScore, threadId, localEvaluations);
                            }
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

        return runBestScore; // Vrátime vyleštené finálne skóre
    }

    // =========================================================================
    // 🌲 EXHAUSTIVE BACKTRACKING VYBRUS: Preskúma úplne všetky stúpajúce vetvy
    // =========================================================================
    private long executeExhaustivePolish(char[] matrix, double currentScore,
                                         char[] bestMatrixInRun, double[] bestScoreInRun,
                                         NGramStats statsSK, NGramStats statsEN,
                                         Weights weights, double skRatio, int depth) {
        // 📈 Aktualizácia historického maxima hĺbky pre hlavný dashboard
        int currentMax;
        while (depth > (currentMax = maxPolishDepth.get())) {
            if (maxPolishDepth.compareAndSet(currentMax, depth)) {
                break;
            }
        }
        // Evidujeme, že sme vstúpili do hĺbky novej vetvy výbrusu
        totalPolishRuns.incrementAndGet();
        long localCounter = 1;

        // Skenujeme všetkých 435 unikátnych dvojíc na tejto úrovni stromu
        for (int i = 0; i < 29; i++) {
            for (int j = i + 1; j < 30; j++) {

                // 1. VYKONAJ SWAP (Krok vpred do hĺbky)
                char temp = matrix[i];
                matrix[i] = matrix[j];
                matrix[j] = temp;

                if (LayoutValidator.isHumanFriendly(matrix)) {
                    LayoutStats sweepSk = Evaluator.evaluate(matrix, statsSK, weights, skMonoX, skBiX, skSkipX, skTriX);
                    LayoutStats sweepEn = Evaluator.evaluate(matrix, statsEN, weights, enMonoX, enBiX, enSkipX, enTriX);
                    double nextScore = (sweepSk.getScore() * skRatio) + (sweepEn.getScore() * (1 - skRatio));

                    // 🔥 STRIKTNE STÚPANIE: Ak táto vetva prináša akékoľvek zlepšenie, ideme dnu!
                    if (nextScore > currentScore) {
                        totalPolishImprovements.incrementAndGet();

                        // Ak sme v tejto vetve prekonali doterajšie maximum TOHTO konkrétneho rekurzívneho behu
                        if (nextScore > bestScoreInRun[0]) {
                            bestScoreInRun[0] = nextScore;
                            System.arraycopy(matrix, 0, bestMatrixInRun, 0, matrix.length);
                        }

                        // Skontrolujeme, či táto vetva náhodou neprepísala absolútny GLOBÁLNY rekord aplikácie
                        checkAndSetGlobalBest(matrix, nextScore, sweepSk.getScore(), sweepEn.getScore(), "VÝBRUS");

                        // 🌀 REKURZÍVNY SKOK: Preskúmame, kam až táto úspešná vetva vedie ďalej
                        localCounter += executeExhaustivePolish(matrix, nextScore, bestMatrixInRun, bestScoreInRun,
                                statsSK, statsEN, weights, skRatio, depth + 1);
                    }
                }

                // 2. KROK SPÄŤ (Rollback) – uvoľníme maticu v pôvodnom stave pre testovanie ďalšej dvojice
                char undo = matrix[i];
                matrix[i] = matrix[j];
                matrix[j] = undo;
            }
        }
        return localCounter;
    }

    // Na začiatku aplikácie nastav: globalBestScore = 0.0 a dynamicThreshold = 0.0
    public void checkAndSetGlobalBest(char[] matrix, double score, double sk, double en, String source) {
        synchronized (lock) {
            if (score > globalBestScore) {
                globalBestScore = score;
                globalBestMatrix = Arrays.copyOf(matrix, matrix.length);

                // 🚀 DYNAMICKÝ REFAKTOR: Latku pre "zaujímavé" layouty odvodíme od celkového skóre.
                // Povolíme zápis layoutom, ktoré za absolútnym kráľom zaostávajú maximálne o 0.3 bodu.
                this.dynamicThreshold = score - 0.3;
                // 🚀 TU: Zapamätáme si, v ktorom celkovom behu tento úspech padol
                bestScoreRun.set(totalEvaluations);
                // 🚀 VYLEPŠENÝ VÝPIS S OZNAČENÍM PÔVODU:
                System.out.printf("🏆 NOVÝ GLOBÁLNY REKORD [%s]! Skóre: %.4f (SK: %.2f | EN: %.2f) -> Latka posunutá na: %.4f%n",
                        source, score, sk, en, this.dynamicThreshold);
            }
        }
    }

}
