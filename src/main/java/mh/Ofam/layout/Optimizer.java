package mh.Ofam.layout;

import java.util.Random;

public class Optimizer {

    private final Random random = new Random();

    public void optimize(Layout initialLayout, TextStats skStats, TextStats enStats) {
        Layout bestLayout = initialLayout.clone();
        Weights weights = new Weights();
        double bestScore = calculateWeightedScore(bestLayout, weights, skStats, enStats);

        System.out.println("Štartovacie skóre: " + bestScore);

        long iteracie = 500_000; // Počítač to zhltne za pár sekúnd
        for (long i = 0; i < iteracie; i++) {

            // 1. Vytvor kópiu doteraz najlepšieho layoutu
            Layout mutated = bestLayout.clone();

            // 2. Náhodne prehoď dva klávesy (mutácia)
            // POZOR: Prehadzuj len alfabetu (písmená), palce alebo pevné znaky vynechaj
            mutateLayout(mutated);

            // 3. Ohodnoť nový layout tvojou vlastnou 80/20 funkciou
            double newScore = calculateWeightedScore(mutated, weights, skStats, enStats);

            // 4. Ak je nový layout lepší, prepíš ten starý
            if (newScore > bestScore) {
                bestScore = newScore;
                bestLayout = mutated;
                System.out.printf("Iterácia %d: Nové najlepšie skóre -> %.4f (Zmena: %s)\n",
                        i, bestScore, mutated.getName());
                bestLayout.print();
            } else {
                if (i % 50 == 0) System.out.println();
                System.out.print(".");
            }
        }

        System.out.println("\n🔥 GENETICKÁ OPTIMALIZÁCIA DOKONČENÁ 🔥");
        System.out.println("Ultimátny layout pre tvoj Totem s hodnotením: " + bestScore);
        bestLayout.print(); // Tvoja metóda na vykreslenie klávesnice
    }

    private double calculateWeightedScore(Layout layout, Weights weights, TextStats sk, TextStats en) {
        // Tvoja existujúca funkcia, ktorá preženie layout tvojím analyzátorom
        double skScore = LayoutStats.instance(sk, layout, weights, true).eliteScore;
//        double enScore = MyAnalyzer.evaluate(layout, en);

        // Tvoja vysnívaná váha 80/20 bez rizika zhltnutia dát
//        return (0.8 * skScore) + (0.2 * enScore);
        return skScore;
    }

    private void mutateLayout(Layout layout) {
        // Jednoduchý swap dvoch náhodných pozícií v matici klávesnice
        int pos1 = random.nextInt(30); // Predpokladáme 30 základných klávesov
        int pos2 = random.nextInt(30);

        while (pos1 == pos2) {
            pos2 = random.nextInt(30);
        }

        char temp = layout.matrix[pos1];
        layout.matrix[pos1] = layout.matrix[pos2];
        layout.matrix[pos2] = temp;
    }
}
