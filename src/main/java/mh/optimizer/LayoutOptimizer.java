package mh.optimizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LayoutOptimizer {

    private final Random random = new Random();

    public void runOptimization(Map<String, Long> skRawWords, Map<String, Long> enRawWords, String initialLayoutStr, long iterations, int maxN) {
        // 1. JEDNORAZOVÉ PREDSPRACOVANIE KORPUSOV
        NGramStats skStats = CorpusProcessor.process(skRawWords, maxN);
//        NGramStats enStats = CorpusProcessor.process(enRawWords, maxN);

        // Inicializácia váh a štartovacieho layoutu
        Weights weights = Weights.builder().build(); // Načíta predvolené 1.0 váhy
        FastLayout bestLayout = new FastLayout("Totem Alpha", initialLayoutStr);

        // Prvá evaluácia
        FastLayoutStats skScore = FastEvaluator.evaluate(bestLayout, skStats, weights);
//        FastLayoutStats enScore = FastEvaluator.evaluate(bestLayout, enStats, weights);
//        double bestGlobalScore = (0.8 * skScore.eliteScore) + (0.2 * enScore.eliteScore);
        double bestGlobalScore = skScore.eliteScore;

        System.out.println("\n🚀 ŠTARTOVACIA EVALUÁCIA DOKONČENÁ");
//        System.out.printf("Pôvodné kombinované skóre: %.4f (SK Elite: %.2f | EN Elite: %.2f)\n\n",
//                bestGlobalScore, skScore.eliteScore, enScore.eliteScore);
        System.out.printf("Pôvodné kombinované skóre: %.4f (SK Elite: %.2f | EN Elite: %.2f)\n\n",
                bestGlobalScore, skScore.eliteScore, 0.0);

        long startTime = System.currentTimeMillis();
        long lastPrintTime = startTime;
        long improvementsCount = 0;

        // 2. HLAVNÁ GENETICKÁ SLUČKA (Hill Climbing)
        for (long i = 0; i < iterations; i++) {

            // Náhodný výber dvoch pozícií na prehodenie (0 až 29)
            int pos1 = random.nextInt(30);
            int pos2 = random.nextInt(30);
            while (pos1 == pos2) {
                pos2 = random.nextInt(30);
            }

            // MUTÁCIA: Prehodíme klávesy priamo v layoute
            bestLayout.swap(pos1, pos2);

            // EVALUÁCIA: Bleskový prelet nad N-gramami (trvá mikrosekundy)
            FastLayoutStats newSk = FastEvaluator.evaluate(bestLayout, skStats, weights);
//            FastLayoutStats newEn = FastEvaluator.evaluate(bestLayout, enStats, weights);

            // Výpočet tvojej vysnívanej váhy 80/20
//            double newGlobalScore = (0.8 * newSk.eliteScore) + (0.2 * newEn.eliteScore);
            double newGlobalScore = newSk.eliteScore;

            // AK JE NOVÝ LAYOUT LEPŠÍ, PONECHÁME SI HO
            if (newGlobalScore > bestGlobalScore) {
                bestGlobalScore = newGlobalScore;
                improvementsCount++;

                System.arraycopy(bestLayout.matrix, 0, bestLayout.matrix, 0, 30); // Potvrdenie zmeny

                // Logovanie úspechu
//                long now = System.currentTimeMillis();
//                if (now - lastPrintTime > 300) { // Ochrana pred zahltením konzoly
//                    System.out.printf("Iterácia %6d: Nový rekord -> %.4f  [SK: %.2f | EN: %.2f] (Zmeny: %d)\n",
//                            i, bestGlobalScore, newSk.eliteScore, newEn.eliteScore, improvementsCount);
                    System.out.printf("Iterácia %6d: Nový rekord -> %.4f  [SK: %.2f | EN: %.2f] (Zmeny: %d)\n",
                            i, bestGlobalScore, newSk.eliteScore, 0.0, improvementsCount);
                    printMatrixBeautifully(bestLayout.matrix);
//                    lastPrintTime = now;
//                }
            } else {
                // ANULÁCIA MUTÁCIE: Ak bol layout horší, vrátime klávesy bleskovo späť
                bestLayout.swap(pos1, pos2);
                if (i % 1000 == 0) System.out.println(i);
            }
        }

        long endTime = System.currentTimeMillis();
        double totalTimeSec = (endTime - startTime) / 1000.0;

        // 3. FINÁLNY REBRLÍČEK A VYPÍSANIE VÍŤAZA
        System.out.println("\n==================================================");
        System.out.println("🔥 OPTIMALIZÁCIA DOKONČENÁ 🔥");
        System.out.printf("Celkový čas behu: %.2f sekúnd (Rýchlosť: %.0f iterácií/s)\n",
                totalTimeSec, (iterations / totalTimeSec));
        System.out.printf("Ultimátne kombinované skóre: %.4f\n", bestGlobalScore);
        System.out.println("==================================================");

        printMatrixBeautifully(bestLayout.matrix);
    }

    private void printMatrixBeautifully(char[] matrix) {
        System.out.println("\nVÝSLEDNÝ LAYOUT PRE TVOJ TOTEM:\n");
        for (int i = 0; i < 30; i++) {
            System.out.print(matrix[i] + "  "); // OPRAVENÉ
            if (i == 4 || i == 14 || i == 24) System.out.print(" |  "); // OPRAVENÉ
            if ((i + 1) % 10 == 0) System.out.println();
        }
        System.out.println();
    }

}
