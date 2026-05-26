package mh.layout.optimizer;

import mh.layout.core.evaluator.Evaluator;
import mh.layout.core.evaluator.Weights;
import mh.layout.core.stats.LayoutStats;
import mh.layout.core.stats.NGramStats;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ParallelOptimizer {

    // Globálna poistka pre bezpečné zastavenie všetkých vlákien z vonku
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Zdieľaná schránka pre absolútne najlepší bilingválny layout, aký kedy všetky vlákna našli
    private char[] globalBestMatrix;
    private double globalBestScore = -Double.MAX_VALUE;
    private final Object globalLock = new Object(); // Zámok len pre zápis absolútneho víťaza

    /**
     * Spustí bleskové multithreadové hľadanie optimálneho layoutu.
     * * @param initialMatrix Vstupný layout, od ktorého sa odrazíme (napr. QWERTY alebo náhodný)
     * @param statsSK       Naplnená schránka slovenského korpusu
     * @param statsEN       Naplnená schránka anglického korpusu
     * @param weights       Uprataná konfigurácia tvojich váh a penalizácií
     * @param skRatio       Pomer dôležitosti slovenčiny (napr. 0.60 pre 60 % SK / 40 % EN)
     */
    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        this.globalBestMatrix = initialMatrix.clone();
        this.running.set(true);

        // Zistíme, koľko reálnych jadier má tvoj procesor k dispozícii
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = Executors.newFixedThreadPool(cores);

        System.out.println("🔥 Štartujem bilingválnu optimalizáciu na " + cores + " jadrách...");
        System.out.println("📊 Pomer jazykov: " + (skRatio * 100) + "% SK / " + ((1.0 - skRatio) * 100) + "% EN");

        // Spustíme nezávislú slučku na každom jadre procesora
        for (int i = 0; i < cores; i++) {
            final int threadId = i;
            threadPool.submit(() -> runWorker(threadId, statsSK, statsEN, weights, skRatio));
        }
    }

    /**
     * Samostatná horúca slučka pre jedno konkrétne jadro procesora.
     */
    private void runWorker(int threadId, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        // Každé vlákno si vyrobí vlastný generátor náhodných čísel a lokálnu kópiu matice,
        // aby sme eliminovali cache-misses a zbytočné premazávanie pamäte medzi jadrami.
        Random rand = new Random();
        char[] localMatrix;
        double localBestScore;

        synchronized (globalLock) {
            localMatrix = globalBestMatrix.clone();
            localBestScore = globalBestScore;
        }

        long iterations = 0;
        double enRatio = 1.0 - skRatio;

        // --- HLAVNÝ PARALELNÝ CYKLUS ---
        while (running.get()) {
            iterations++;

            // 1. MUTÁCIA: Náhodne vyberieme dve pozície z 30 klávesov a prehodíme ich
            int pos1 = rand.nextInt(30);
            int pos2 = rand.nextInt(30);
            while (pos1 == pos2) {
                pos2 = rand.nextInt(30);
            }

            // Urobíme fyzický swap (prehodenie)
            char temp = localMatrix[pos1];
            localMatrix[pos1] = localMatrix[pos2];
            localMatrix[pos2] = temp;

            // 2. BILINGVÁLNA EVALUÁCIA: Obehneme naše ploché jadro pre oba jazyky nezávisle
            LayoutStats skStats = Evaluator.evaluate(localMatrix, statsSK, weights);
            LayoutStats enStats = Evaluator.evaluate(localMatrix, statsEN, weights);

            // Spojíme výsledky do jednej kombinovanej bilingválnej pravdy
            double combinedScore = (skStats.getEliteScore() * skRatio) + (enStats.getEliteScore() * enRatio);

            // 3. ROZHODNUTIE (Hill Climbing)
            if (combinedScore > localBestScore) {
                // Našli sme lepší lokálny layout! Zapamätáme si ho
                localBestScore = combinedScore;

                // Skontrolujeme, či je tento lokálny úspech lepší ako celosvetový rekord všetkých jadier
                synchronized (globalLock) {
                    if (combinedScore > globalBestScore) {
                        globalBestScore = combinedScore;
                        globalBestMatrix = localMatrix.clone();

                        // Informujeme ťa do konzoly, že optimizér práve urobil evolučný skok vpred
                        System.out.printf("[Jadro %d] 🎉 Nový bilingválny rekord: %.4f (po %d iteráciách)\n",
                                threadId, globalBestScore, iterations);
                    }
                }
            } else {
                // Mutácia bola krok vedľa, prsty by trpeli -> vrátime swap späť do pôvodného stavu
                localMatrix[localMatrix[pos2] = localMatrix[pos1]] = temp; // Rýchly inline spätný chod
                localMatrix[pos1] = localMatrix[pos2];
                localMatrix[pos2] = temp;
            }

            // Periodický výpis statusu raz za milión pokusov (voliteľné pre kontrolu tempa)
            if (iterations % 10_000_000 == 0) {
                System.out.println("[Jadro " + threadId + "] Preletených 10 miliónov rozložení...");
            }
        }
    }

    /**
     * Zastaví všetky bežiace jadrá a vráti absolútneho bilingválneho víťaza.
     */
    public char[] stopAndGetBest() {
        this.running.set(false);
        synchronized (globalLock) {
            return globalBestMatrix.clone();
        }
    }
}
