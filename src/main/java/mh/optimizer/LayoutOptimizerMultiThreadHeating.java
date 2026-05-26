package mh.optimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LayoutOptimizerMultiThreadHeating {
    public static final int THRESHOLD_SCORE_SK = 80;
    public static final int THRESHOLD_SCORE_EN = 70;

    private final Object globalLock = new Object();

    // 🔥 NOVINKA: Deterministické anatomické pravidlá na brutálne orezanie vyhľadávacieho priestoru
    private static boolean isValidTarget(char ch, int pos) {
        // Pravidlo 1: Samohlásky striktne len na pravú ruku (Dvorakov princíp striedania rúk)
        if (ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u' || ch == 'y') {
            if (KeyboardTopology.POS_TO_HAND[pos] != 1) return false;
        }

        // Pravidlo 2: Elita korpusu nesmie nikdy opustiť stredný domovský riadok
        if (ch == 'e' || ch == 'a' || ch == 'o' || ch == 'i' || ch == 't' || ch == 'r' || ch == 's') {
            if (KeyboardTopology.POS_TO_ROW[pos] != 1) return false;
        }

        // Pravidlo 3: Vzácny odpad (q, x, w) nesmie parazitovať na najpohodlnejšom strednom riadku
        if (ch == 'q' || ch == 'x' || ch == 'w') {
            if (KeyboardTopology.POS_TO_ROW[pos] == 1) return false;
        }

        return true;
    }

    public void runOptimization(Map<String, Long> skRawWords, Map<String, Long> enRawWords, String initialLayoutStr, long iterations, int maxN) {
        // 1. JEDNORAZOVÉ PREDSPRACOVANIE (Oba korpusy očistíme cez štartovaciu maticu)
        FastLayout startLayout = new FastLayout("StartCheck", initialLayoutStr);
        NGramStats skStats = CorpusProcessor.process(skRawWords, maxN, startLayout.matrix);
        NGramStats enStats = CorpusProcessor.process(enRawWords, maxN, startLayout.matrix);
        Weights weights = Weights.builder().build();

        // Zistíme počet jadier procesora (u teba 8)
        int threadsCount = 5; // Runtime.getRuntime().availableProcessors() / 2;
        if (threadsCount < 1) threadsCount = 1;

        // NASTAVENIE POČIATOČNEJ BILINGVÁLNEJ LATKY
        FastLayoutStats startSk = FastEvaluator.evaluate(startLayout, skStats, weights);
        FastLayoutStats startEn = FastEvaluator.evaluate(startLayout, enStats, weights);

        System.out.println("\n🔥 NASTAVENÝ POČET PROCESOROVÝCH JADIER: " + threadsCount);
        System.out.printf("Štartovacia latka: SK: %.4f | EN: %.4f\n\n", startSk.eliteScore, startEn.eliteScore);

        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        List<Future<OptimizationResult>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // 2. PARALELNÉ WORKERY PRE KAŽDÉ JADRO
        for (int t = 0; t < threadsCount; t++) {
            final int workerId = t;
            futures.add(executor.submit(() -> {
                Random random = new Random();
                String workerName = "Jadro-" + workerId;

                // Každé vlákno začne s východiskovým layoutom
                FastLayout currentLayout = new FastLayout(workerName, initialLayoutStr);

                // MALÝ TRIK: Prvých pár sekúnd rozhádžeme klávesy náhodne
                if (workerId > 0) {
                    for (int s = 0; s < 50; s++) {
                        currentLayout.swap(random.nextInt(30), random.nextInt(30));
                    }
                }

                FastLayoutStats skS = FastEvaluator.evaluate(currentLayout, skStats, weights);
                FastLayoutStats enS = FastEvaluator.evaluate(currentLayout, enStats, weights);
                double currentSkScore = skS.eliteScore;
                double currentEnScore = enS.eliteScore;

                // ==========================================
                // 🔥 DYNAMICKÉ NASTAVENIE SIMULOVANÉHO ŽÍHANIA
                // ==========================================
                // 💥 OPRAVA: Jadro-0 stráži dofX a nezažíva teplotný šok. Ostatné idú divokú tavbu.
                double temp = (workerId == 0) ? 0.001 : 10.0;
                double finalTemp = 0.001;    // Cieľová teplota zmrazenia (Hill Climber)

                // 💥 ZMENA: Výpočet chladenia presne na mieru podľa zadaných iterácií
                double coolingRate = Math.pow(finalTemp / temp, 1.0 / iterations);
                // ==========================================

// ==================================================
// NOVÁ, SVALNATÁ SLUČKA PRE TVOJ MULTITHREAD
// ==================================================
                long workerStartTime = System.currentTimeMillis();

                for (long i = 0; i < iterations; i++) {

                    // Priebežný živý odhad rýchlosti (každých 10 000 krokov)
                    if (i % 10_000 == 0 && i > 0) {
                        long currentNow = System.currentTimeMillis();
                        double elapsedSec = (currentNow - workerStartTime) / 1000.0;
                        double currentIps = elapsedSec > 0 ? (i / elapsedSec) : 0;
                        System.out.printf("   [%s] Priebeh: %6d / %d | Rýchlosť: %.0f it/s (Aktuálne: %.4f | %.4f) | Teplota: %.4f\n",
                                workerName, i, iterations, currentIps, currentSkScore, currentEnScore, temp);
                    }

                    // Čistá, slobodná mutácia dvoch náhodných pozícií
                    int pos1 = random.nextInt(30);
                    int pos2 = random.nextInt(30);
                    while (pos1 == pos2) {
                        pos2 = random.nextInt(30);
                    }

                    // MUTÁCIA: Prehodíme klávesy bez akéhokoľvek diktátu a predsudkov
                    currentLayout.swap(pos1, pos2);

                    // EVALUÁCIA: Nech o kvalite rozhodne čistá matematika tvojho FastEvaluatora
                    FastLayoutStats newSk = FastEvaluator.evaluate(currentLayout, skStats, weights);
                    FastLayoutStats newEn = FastEvaluator.evaluate(currentLayout, enStats, weights);

//                    // Poistka: Ak by po mutácii spadol hociktorý jazyk pod 80, okamžitý rollback
//                    if (newSk.eliteScore < THRESHOLD_SCORE || newEn.eliteScore < THRESHOLD_SCORE) {
//                        currentLayout.swap(pos1, pos2);
//                        continue;
//                    }

                    // Matematická delta žíhania
                    double deltaSk = newSk.eliteScore - currentSkScore;
                    double deltaEn = newEn.eliteScore - currentEnScore;

                    boolean acceptSk = deltaSk > 0 || (temp > 0.001 && random.nextDouble() < Math.exp(deltaSk / temp));
                    boolean acceptEn = deltaEn > 0 || (temp > 0.001 && random.nextDouble() < Math.exp(deltaEn / temp));

                    // PODMIENKA ŽÍHANIA
                    if (acceptSk || acceptEn) {
                        currentSkScore = newSk.eliteScore;
                        currentEnScore = newEn.eliteScore;

                        // Systém si sám skontroluje, či skore splna poziadavky a zapíše ho na disk/obrazovku
                        char[] matrixCopy = currentLayout.matrix.clone();
                        checkAndReportGlobalImprovement(workerName, matrixCopy, currentSkScore, currentEnScore, i);
                    } else {
                        currentLayout.swap(pos1, pos2); // rollback
                    }

                    temp *= coolingRate;
                }

                return new OptimizationResult(currentLayout.matrix, currentSkScore, currentEnScore);
            }));
        }

        // 3. ZBER VÝSLEDKOV (Len počkáme na tiché dobehnutie všetkých vlákien)
        try {
            for (Future<OptimizationResult> future : futures) {
                OptimizationResult res = future.get(); // Čaká na dokončenie konkrétneho jadra
                System.out.printf("📢 [%s] dokončilo prácu. Aktualne skóre sk: %.4f | en: %.4f\n", Thread.currentThread().getName(), res.scoreSk, res.scoreEn);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        // 4. FINÁLNE UTRETIE STOLA
        long endTime = System.currentTimeMillis();
        double totalTimeSec = (endTime - startTime) / 1000.0;

        System.out.println("\n==================================================");
        System.out.printf("🔥 OPTIMALIZÁCIA DOKONČENÁ za %.2f sekúnd 🔥\n", totalTimeSec);
        System.out.println("Všetky top bilingválne rozloženia máš uložené v logu.");
        System.out.println("==================================================");
    }

    private void checkAndReportGlobalImprovement(String workerName, char[] matrix, double scoreSk, double scoreEn, long iteration) {
        synchronized (globalLock) {
            if (scoreSk > THRESHOLD_SCORE_SK && scoreEn > THRESHOLD_SCORE_EN) {

                // 1. Klasický výpis do konzoly, ako doteraz
                System.out.printf("\n🚀 [%s] Iterácia %6d: !!! NOVÝ ZAPIS -> %.4f | %.4f !!!\n  Layout: ",
                        workerName, iteration, scoreSk, scoreEn);

                StringBuilder layoutSb = new StringBuilder();
                for (int i = 0; i < 30; i++) {
                    layoutSb.append(matrix[i]);
                    if (i == 9 || i == 19) layoutSb.append(" / ");
                }
                System.out.println(layoutSb.toString() + "\n");

                // 🔥 2. ZÁCHRANNÁ ČIERNA SKRINKA: Okamžitý zápis na disk
                try (java.io.FileWriter fw = new java.io.FileWriter("totem_rekordy.txt", true);
                     java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                    pw.printf("[%s] Iteracia: %d | SKOREsk: %.4f | SKOREen: %.4f | Layout: %s\n",
                            workerName, iteration, scoreSk, scoreEn, layoutSb.toString());
                } catch (java.io.IOException e) {
                    // Ak zápis zlyhá, nechceme, aby nám to zhodilo celú optimalizáciu
                }
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
        final double scoreSk;
        final double scoreEn;

        OptimizationResult(char[] matrix, double scoreSk, double scoreEn) {
            this.matrix = matrix;
            this.scoreSk = scoreSk;
            this.scoreEn = scoreEn;
        }
    }
}
