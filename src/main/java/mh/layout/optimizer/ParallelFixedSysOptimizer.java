package mh.layout.optimizer;

import mh.layout.analyzer.AnalyzedLayout;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ParallelFixedSysOptimizer {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 🗲 DYNAMICKÁ MATICA: 24 permutácií symbolov detegovaných pri štarte z initialMatrix
    private final char[][] symbolPermutations = new char[24][4];

    public volatile double dynamicThreshold = 0.0;

    private char[] globalBestMatrix;
    private double globalBestScore = -1.0;

    private final Object lock = new Object();
    private final Object journalLock = new Object();

    private long totalEvaluations = 0;
    private static final String JOURNAL_FILE = "c:\\home\\project-personal\\layout-analyzer\\layout_journal.txt";

    private double skMono, skBigram, skSkip, skTri;
    private double enMono, enBigram, enSkip, enTri;

    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        if (isRunning.get()) return;
        isRunning.set(true);

        // 1. AUTOMATICKÁ DETEKCIA SYMBOLOV NA VSTUPE (Žiadny hardcode)
        char[] detectedSymbols = detectSymbolsFromInput(initialMatrix);

        // 2. GENÉZA PERMUTÁCIÍ PRE DETEGOVANÚ INTERPUNKCIU
        generateDynamicPermutations(detectedSymbols);

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

        double enRatio = 1.0 - skRatio;
        this.globalBestMatrix = Arrays.copyOf(initialMatrix, initialMatrix.length);
        LayoutStats initSk = Evaluator.evaluate(globalBestMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
        LayoutStats initEn = Evaluator.evaluate(globalBestMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);
        this.globalBestScore = (initSk.getScore() * skRatio) + (initEn.getScore() * enRatio);
        this.dynamicThreshold = this.globalBestScore - 0.3;

        // 3. PRÍSNY GENERÁTOR ŠABLÓN (Kombinatorika prehnaná tvojím prísnym kognitívnym sitom)
        List<int[]> allValidTemplates = generateValidTemplates();

        int cores = Runtime.getRuntime().availableProcessors() / 2;
        System.out.println("🔥 Štartujem ParallelOptimizer na " + cores + " jadrách procesora...");
        System.out.println("🎯 Detegované vstupné symboly: " + Arrays.toString(detectedSymbols));
        System.out.println("🏛️ Počet prísne schválených geometrií v poole: " + allValidTemplates.size());
        System.out.println("📝 Pociatocny threshold na zapis do dennika " + JOURNAL_FILE + " je " + dynamicThreshold);

        // 4. DISTRIBÚCIA ÚLOH PRE JADRÁ
        for (int i = 0; i < cores; i++) {
            List<int[]> coreTemplates = new ArrayList<>();
            for (int t = i; t < allValidTemplates.size(); t += cores) {
                coreTemplates.add(allValidTemplates.get(t));
            }

            final int threadId = i;
            Thread worker = new Thread(() -> runAnnealingWorker(threadId, coreTemplates, initialMatrix, statsSK, statsEN, weights, skRatio));
            worker.setDaemon(true);
            worker.start();
        }
    }

    private char[] detectSymbolsFromInput(char[] matrix) {
        char[] syms = new char[4];
        int count = 0;
        for (char c : matrix) {
            if (c < 'A' || c > 'Z') {
                if (count < 4) syms[count++] = c;
            }
        }
        if (count < 4) throw new IllegalArgumentException("Kritická chyba: Inicializačná matica neobsahuje 4 symboly!");
        return syms;
    }

    private void generateDynamicPermutations(char[] syms) {
        int count = 0;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                if (b == a) continue;
                for (int c = 0; c < 4; c++) {
                    if (c == a || c == b) continue;
                    for (int d = 0; d < 4; d++) {
                        if (d == a || d == b || d == c) continue;
                        symbolPermutations[count++] = new char[]{syms[a], syms[b], syms[c], syms[d]};
                    }
                }
            }
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
    // ⚙️ NEKONEČNÉ ŽÍHANIE S PER-TEMPLATE PAMÄŤOU (MAKRO-EVOLÚCIA)
    // =========================================================================
    private void runAnnealingWorker(int threadId, List<int[]> myTemplates, char[] startMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        Random rand = new Random();
        double enRatio = 1.0 - skRatio;
        long localEvaluations = 0;

        int numTemplates = myTemplates.size();

        // --- 🚀 PER-TEMPLATE PAMÄŤ JADRA ---
        // Aby si jadro pamätalo evolučný vývoj každej šablóny, kým rotuje zoznam taskov
        char[][] currentMatrices = new char[numTemplates][30];
        char[][] localBestMatrices = new char[numTemplates][30];
        double[] localBestScores = new double[numTemplates];
        Arrays.fill(localBestScores, -1.0);

        // Inicializácia východiskového stavu pre všetky pridelené šablóny
        for (int i = 0; i < numTemplates; i++) {
            currentMatrices[i] = Arrays.copyOf(startMatrix, startMatrix.length);
            int[] t = myTemplates.get(i);
            // Nastavíme úvodné platné symboly, aby prvá evaluácia nespadla na duplicitách
            currentMatrices[i][t[0]] = symbolPermutations[0][0];
            currentMatrices[i][t[1]] = symbolPermutations[0][1];
            currentMatrices[i][t[2]] = symbolPermutations[0][2];
            currentMatrices[i][t[3]] = symbolPermutations[0][3];
            localBestMatrices[i] = Arrays.copyOf(currentMatrices[i], 30);
        }

        // BEŽÍME DO NEKONEČNA (Presne podľa tvojho pôvodného herného plánu)
        while (isRunning.get()) {

            // Jadro sekvenčne prejde svoj balík taskov, zmrazí ich a pri ďalšom prechode znova zahreje
            for (int tIdx = 0; tIdx < numTemplates; tIdx++) {
                if (!isRunning.get()) break;

                int[] lockedIndices = myTemplates.get(tIdx);
                int[] freeAlphabetIndices = calculateFreeIndices(lockedIndices);

                // 🔥 INTELIGENTNÝ RE-HEATING (Návrat ku koreňom konkrétnej šablóny)
                // Načítame doterajší najlepší evolučný stav tejto šablóny a znova ju zapálime na teplote 3.0
                char[] currentMatrix = Arrays.copyOf(localBestMatrices[tIdx], 30);
                char[] localBestMatrix = Arrays.copyOf(localBestMatrices[tIdx], 30);
                double localBestScore = localBestScores[tIdx];

                LayoutStats initSk = Evaluator.evaluate(currentMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
                LayoutStats initEn = Evaluator.evaluate(currentMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);
                double currentScore = (initSk.getScore() * skRatio) + (initEn.getScore() * enRatio);

                if (localBestScore == -1.0) {
                    localBestScore = currentScore;
                    localBestScores[tIdx] = currentScore;
                }

                double temperature = 3.0; // Štartovací žiar pre novú vlnu výbrusu
                final double coolingRate = 0.000005;

                // Mikroskopické mrazenie konkrétnej šablóny
                while (isRunning.get() && temperature > 0.05) {

                    // 1. Náhodný výber dvoch pozícií pre swap abecedy (Mimo zamknutých symbolov)
                    int idx1 = freeAlphabetIndices[rand.nextInt(freeAlphabetIndices.length)];
                    int idx2 = freeAlphabetIndices[rand.nextInt(freeAlphabetIndices.length)];
                    if (idx1 == idx2) continue;

                    // 2. Vykonaj swap abecedy (Krok vpred)
                    char temp = currentMatrix[idx1];
                    currentMatrix[idx1] = currentMatrix[idx2];
                    currentMatrix[idx2] = temp;

                    // 3. 🗲 BRUTÁLNY VNÚTORNÝ SKEN: Paralelne otestujeme všetkých 24 tvárí interpunkcie pre túto novú abecedu
                    double bestScoreForThisStep = -1.0;
                    int bestPermIndexForThisStep = 0;
                    LayoutStats bestSkStatsForThisStep = null;
                    LayoutStats bestEnStatsForThisStep = null;

                    for (int p = 0; p < 24; p++) {
                        currentMatrix[lockedIndices[0]] = symbolPermutations[p][0];
                        currentMatrix[lockedIndices[1]] = symbolPermutations[p][1];
                        currentMatrix[lockedIndices[2]] = symbolPermutations[p][2];
                        currentMatrix[lockedIndices[3]] = symbolPermutations[p][3];

                        localEvaluations++;
                        if ((localEvaluations & 0x0FFF) == 0) {
                            synchronized (lock) { totalEvaluations += 0x0FFF; }
                        }

                        LayoutStats nextSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
                        LayoutStats nextEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);
                        double nextScore = (nextSkStats.getScore() * skRatio) + (nextEnStats.getScore() * enRatio);

                        if (nextScore > bestScoreForThisStep) {
                            bestScoreForThisStep = nextScore;
                            bestPermIndexForThisStep = p;
                            bestSkStatsForThisStep = nextSkStats;
                            bestEnStatsForThisStep = nextEnStats;
                        }
                    }

                    // 4. Zafixujeme absolútne najlepšie interpunkčné rozloženie pre tento krok abecedy
                    currentMatrix[lockedIndices[0]] = symbolPermutations[bestPermIndexForThisStep][0];
                    currentMatrix[lockedIndices[1]] = symbolPermutations[bestPermIndexForThisStep][1];
                    currentMatrix[lockedIndices[2]] = symbolPermutations[bestPermIndexForThisStep][2];
                    currentMatrix[lockedIndices[3]] = symbolPermutations[bestPermIndexForThisStep][3];

                    double skScore = bestSkStatsForThisStep.getScore();
                    double enScore = bestEnStatsForThisStep.getScore();

                    // Splachovanie info do konzoly podľa tvojej masky
                    if ((localEvaluations & 0x0FFFF) == 0) {
                        System.out.printf("[Jadro %d] Brúsim šablónu %d/%d... SK: %.4f EN: %.4f (Teplota: %.2f)%n",
                                threadId, (tIdx + 1), numTemplates, skScore, enScore, temperature);
                    }

                    // 📝 STRICT ZÁPIS DO DENNÍKA (Riadi sa tvojím živým dynamickým thresholdom)
                    if (bestScoreForThisStep >= dynamicThreshold) {
                        System.out.println("📝 Zápis do denníka: " + formatMatrix(currentMatrix) + " SK: " + String.format("%.4f", skScore) + " | EN: " + String.format("%.4f", enScore) + " | Jadro: " + threadId);
                        appendToJournal(currentMatrix, skScore, enScore, bestScoreForThisStep, threadId, localEvaluations);
                    }

                    // 5. METODIKA ROZHODOVANIA SIMULOVANÉHO ŽÍHANIA
                    if (bestScoreForThisStep > currentScore) {
                        currentScore = bestScoreForThisStep;

                        if (currentScore > localBestScore) {
                            localBestScore = currentScore;
                            localBestMatrix = Arrays.copyOf(currentMatrix, currentMatrix.length);
                        }

                        checkAndSetGlobalBest(currentMatrix, currentScore, skScore, enScore);
                    } else {
                        double delta = currentScore - bestScoreForThisStep;
                        double acceptanceProbability = Math.exp(-delta / temperature);

                        if (rand.nextDouble() < acceptanceProbability) {
                            currentScore = bestScoreForThisStep;
                        } else {
                            // Krok späť (Undo kroku abecedy)
                            char undo = currentMatrix[idx1];
                            currentMatrix[idx1] = currentMatrix[idx2];
                            currentMatrix[idx2] = undo;
                        }
                    }

                    // Chladnutie
                    temperature *= (1.0 - coolingRate);
                }

                // --- 💾 ZÁLOHA EVOLUČNÉHO POKROKU ŠABLÓNY ---
                // Predtým než jadro preskočí na ďalší task, zapíše si aktuálny stav do poľa, kde počká na ďalší re-heat
                currentMatrices[tIdx] = Arrays.copyOf(currentMatrix, 30);
                localBestMatrices[tIdx] = Arrays.copyOf(localBestMatrix, 30);
                localBestScores[tIdx] = localBestScore;
            }
        }
    }

    private int[] calculateFreeIndices(int[] locked) {
        int[] free = new int[26];
        int count = 0;
        for (int i = 0; i < 30; i++) {
            final int currentPos = i;
            if (java.util.Arrays.stream(locked).noneMatch(x -> x == currentPos)) {
                free[count++] = i;
            }
        }
        return free;
    }

    public void checkAndSetGlobalBest(char[] matrix, double score, double sk, double en) {
        synchronized (lock) {
            if (score > globalBestScore) {
                globalBestScore = score;
                globalBestMatrix = Arrays.copyOf(matrix, matrix.length);
                this.dynamicThreshold = score - 0.3;

                System.out.printf("%n🏆 NOVÝ GLOBÁLNY REKORD! Skóre: %.4f (SK: %.2f | EN: %.2f) -> Latka pre zápis posunutá na celkové skóre: %.4f%n",
                        score, sk, en, this.dynamicThreshold);
            }
        }
    }

    private void appendToJournal(char[] matrix, double sk, double en, double combined, int threadId, long iteration) {
        synchronized (journalLock) {
            try (FileWriter fw = new FileWriter(JOURNAL_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.printf("%s | SK: %.4f | EN: %.4f | COMBINED: %.4f | %s (Core %d) | Iteration: %d%n",
                        formatMatrix(matrix), sk, en, combined, timestamp, threadId, iteration);

            } catch (IOException e) {
                System.err.println("Chyba zápisu do denníka: " + e.getMessage());
            }
        }
    }

    public static String formatMatrix(char[] matrix) {
        if (matrix == null || matrix.length != 30) return "";
        StringBuilder sb = new StringBuilder(37);
        sb.append(matrix, 0, 5).append(' ').append(matrix, 5, 5).append("  ");
        sb.append(matrix, 10, 5).append(' ').append(matrix, 15, 5).append("  ");
        sb.append(matrix, 20, 5).append(' ').append(matrix, 25, 5);
        return sb.toString();
    }

    // =========================================================================
    // 👑 PRÍSNY GENERÁTOR ŠABLÓN (Zafixovaný podľa tvojich kognitívnych zákonov)
    // =========================================================================
    public List<int[]> generateValidTemplates() {
        List<int[]> validTemplates = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            for (int j = i + 1; j < 28; j++) {
                for (int k = j + 1; k < 29; k++) {
                    for (int l = k + 1; l < 30; l++) {
                        int[] template = {i, j, k, l};
                        if (isGeometryHumanFriendly(template)) {
                            validTemplates.add(template);
                        }
                    }
                }
            }
        }
        return validTemplates;
    }

    private boolean isGeometryHumanFriendly(int[] idx) {
        // --- 🛑 PRÍSNA STRÁŽNA BRÁNA PRE HOME ROW ---
        // Akýkoľvek symbol na strednom riadku (indexy 10 až 19) odpálime, pokiaľ nestojí na 14 alebo 15
        for (int i = 0; i < 4; i++) {
            int pos = idx[i];
            int row = pos / 10;
            if (row == 1) {
                if (pos != 14 && pos != 15) {
                    return false; // Absolútny stop pre driftovanie interpunkcie po domovských prstoch!
                }
            }
        }

        int[] r = new int[4];
        int[] c = new int[4];
        int countCol4 = 0;
        int countCol5 = 0;

        for (int i = 0; i < 4; i++) {
            r[i] = idx[i] / 10;
            c[i] = idx[i] % 10;
            if (c[i] == 4) countCol4++;
            if (c[i] == 5) countCol5++;
        }

        // VZOREC 5: 3+1 Centrálny Stretch Pilier
        if ((countCol4 == 3 && countCol5 == 1) || (countCol4 == 1 && countCol5 == 3)) return true;

        // VZOREC 4: Centrálny pilier (Ukazovákový stretch zips 2x2 proti sebe)
        if (idx[0] == 4 && idx[1] == 5 && idx[2] == 14 && idx[3] == 15) return true;
        if (idx[0] == 14 && idx[1] == 15 && idx[2] == 24 && idx[3] == 25) return true;

        // VZOREC 1: 4 symboly v jednom riadku lineárne vedľa seba
        if (r[0] == r[1] && r[1] == r[2] && r[2] == r[3]) {
            if (c[1] == c[0] + 1 && c[2] == c[1] + 1 && c[3] == c[2] + 1) return true;
        }

        // VZOREC 2: Vertikálna symetria (Dvojičky nad sebou v rovnakých stĺpcoch)
        if (c[0] == c[1] && c[2] == c[3] && r[0] != r[1] && r[2] != r[3]) return true;
        if (c[0] == c[2] && c[1] == c[3] && r[0] != r[2] && r[1] != r[3]) return true;

        // VZOREC 3: Horizontálne zrkadlo rúk (2 na ľavej ruke, 2 na pravej ruke)
        if (c[0] < 5 && c[1] < 5 && c[2] >= 5 && c[3] >= 5) {
            if (r[0] == r[3] && c[0] == 9 - c[3] && r[1] == r[2] && c[1] == 9 - c[2]) return true;
            if (r[0] == r[2] && c[0] == 9 - c[2] && r[1] == r[3] && c[1] == 9 - c[3]) return true;
        }

        return false;
    }

    public boolean isHumanFriendly(char[] matrix) {
        int[] idx = new int[4];
        int count = 0;
        // PRÍSNA OPRAVA: Plnenie poľa reálnymi indexmi pozícií symbolov v matici
        for (int i = 0; i < matrix.length; i++) {
            char c = matrix[i];
            if (c < 'A' || c > 'Z') {
                if (count < 4) {
                    idx[count++] = i;
                } else {
                    break;
                }
            }
        }
        if (count < 4) return false;
        Arrays.sort(idx);
        return isGeometryHumanFriendly(idx); // Zjednotenie filtrov na rovnakú prísnu geometriu
    }
}
