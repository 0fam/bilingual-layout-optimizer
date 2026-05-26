package mh.layout;

import mh.layout.core.evaluator.Weights;
import mh.layout.core.io.CorpusProcessor;
import mh.layout.core.stats.NGramStats;
import mh.layout.optimizer.ParallelOptimizer;
import java.io.IOException;
import java.util.Map;

public final class Main {

    public static void main(String[] args) {
        System.out.println("🚀 ŠTARTUJEM BILINGVÁLNY TOTEM LAYOUT OPTIMIZER 🚀\n");

        // =========================================================================
        // 📦 1. PRÍPRAVA DÁTOVÝCH SCHRÁNOK
        // =========================================================================
        NGramStats statsSK = new NGramStats();
        NGramStats statsEN = new NGramStats();

        Map<String, Double> slovakDictionary = null;
        Map<String, Double> englishDictionary = null;

        // =========================================================================
        // 📥 2. NAČÍTANIE A ČISTENIE KORPUSOV (Každý jazyk zvlášť do vlastnej krabice)
        // =========================================================================
        try {
            System.out.println("📖 Načítavam slovenský korpus...");
            // Cesta k tvojmu súboru, váha 1.0 (surová frekvencia)
            slovakDictionary = CorpusProcessor.processFile("data/slovak_corpus.txt", statsSK, 1.0);
            System.out.println("✅ SK Korpus úspešne spracovaný. Unikátnych kusov slov (>=4): " + slovakDictionary.size());

            System.out.println("📖 Načítavam anglický korpus...");
            // Cesta k anglickému súboru, váha 1.0
            englishDictionary = CorpusProcessor.processFile("data/english_corpus.txt", statsEN, 1.0);
            System.out.println("✅ EN Korpus úspešne spracovaný. Unikátnych kusov slov (>=4): " + englishDictionary.size());

        } catch (IOException e) {
            System.err.println("❌ Chyba pri načítaní korpusov! Skontroluj, či súbory v zložke 'data/' reálne existujú.");
            e.printStackTrace();
            return;
        }

        // =========================================================================
        // ⚖️ 3. NASTAVENIE ERGONOMICKÝCH VÁH (Tuning tvojho algoritmu)
        // =========================================================================
        Weights weights = new Weights();
        weights.setHomeRow(10.0);     // Vysoká odmena za domovský riadok
        weights.setAlt(5.0);         // Odmena za striedanie rúk ľavá/pravá
        weights.setRollIn(3.0);       // Odmena za plynulé valenie prstov dnu
        weights.setRollOut(1.5);      // Menšia odmena za valenie prstov von

        weights.setStretch(4.0);      // Trest za naťahovanie prstov do diaľky
        weights.setFingerLoad(6.0);   // Tvrdý trest za preťaženie jedného prsta (RSI poistka)
        weights.setSfb(12.0);         // Brutálny trest za ten istý prst dvakrát po sebe
        weights.setSfs(4.0);          // Trest za ten istý prst cez jeden znak (sendviče)
        weights.setScissors(7.0);     // Trest za kríženie susedných prstov (nožnice)
        weights.setRedirects(5.0);    // Trest za trigramové zmeny smeru na jednej ruke
        weights.setLsb(3.0);          // Trest za bočné uličky ukazovákov
        weights.setLhb(4.0);          // Trest za slabú dvojicu malíček + prstenník

        // =========================================================================
        // 🗺️ 4. ŠTARTOVACÍ LAYOUT (30 klávesov Totemu - QWERTY základ + 4 symboly)
        // =========================================================================
        // Indexy 0-9: horný riadok, 10-19: domovský riadok, 20-29: spodný riadok
        char[] initialLayout = new char[] {
                'Q', 'W', 'E', 'R', 'T',   'Y', 'U', 'I', 'O', 'P',
                'A', 'S', 'D', 'F', 'G',   'H', 'J', 'K', 'L', ',',
                'Z', 'X', 'C', 'V', 'B',   'N', 'M', '.', '?', '-'
        };

        // =========================================================================
        // 🏎️ 5. SPUSTENIE PARALELNEJ MAŠINY
        // =========================================================================
        ParallelOptimizer optimizer = new ParallelOptimizer();

        // Pomer dôležitosti jazykov: 60 % dôležitosť pre SK / 40 % pre EN
        double slovakRatio = 0.60;

        optimizer.startOptimization(initialLayout, statsSK, statsEN, weights, slovakRatio);

        // =========================================================================
        // ⏳ 6. ČASOVÝ LIMIT PRE EVALUÁCIU (Necháme jadrá piecť 10 sekúnd)
        // =========================================================================
        try {
            Thread.sleep(10000); // 10 000 milisekúnd = 10 sekúnd optimalizácie
        } catch (InterruptedException e) {
            System.err.println("❌ Optimalizácia bola nečakane prerušená!");
        }

        // =========================================================================
        // 🛑 7. ZASTAVENIE A ZOBRAZENIE REKORDÉRA
        // =========================================================================
        char[] bestLayout = optimizer.stopAndGetBest();
        System.out.println("\n🛑 Optimalizácia ukončená. Sťahujem dáta z jadier...");

        printLayoutMatrix(bestLayout);

        // TODO: Sem v ďalšom kroku zavesíme tvoj Analyzátor, ktorý zoberie 'bestLayout',
        // slovakDictionary a englishDictionary a vytlačí tie parádne záverečné štatistiky.
    }

    /**
     * Vizuálna pomôcka, ktorá ti v konzole vykreslí layout v tvare 3x10 mriežky Totemu.
     */
    private static void printLayoutMatrix(char[] matrix) {
        System.out.println("\n👑 ABSOLÚTNY VÍTʼAZNÝ BILINGVÁLNY LAYOUT 👑");
        System.out.println("-----------------------------------------");
        for (int i = 0; i < 30; i++) {
            System.out.print("[" + matrix[i] + "] ");
            // Každých 10 klávesov zlomíme riadok
            if ((i + 1) % 10 == 0) {
                System.out.println();
            }
            // Pre lepšiu vizualizáciu rozdelenia rúk urobíme medzeru medzi 5. a 6. klávesom
            else if ((i + 1) % 5 == 0) {
                System.out.print("   ");
            }
        }
        System.out.println("-----------------------------------------");
    }
}
