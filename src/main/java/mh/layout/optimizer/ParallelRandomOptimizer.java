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
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static mh.layout.topology.KeyboardTopology.KEY_COUNT;

public final class ParallelRandomOptimizer {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public volatile double dynamicThreshold = 0.0;

    private char[] globalBestMatrix;
    private double globalBestScore = -1.0;

    private final Object lock = new Object();
    private final Object journalLock = new Object();

    private long totalEvaluations = 0;
    private static final String JOURNAL_FILE = "c:\\home\\project-personal\\layout-analyzer\\layout_journal.txt";

    // 🚀 INVARIANTNÉ MENOVATELE: Vypočítané iba raz na začiatku pre brutálny návrat rýchlosti
    private double skMono, skBigram, skSkip, skTri;
    private double enMono, enBigram, enSkip, enTri;

    public void startOptimization(char[] initialMatrix, NGramStats statsSK, NGramStats statsEN, Weights weights, double skRatio) {
        if (isRunning.get()) return;
        isRunning.set(true);

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
        // Prvé inicializačné hodnotenie (Miesto 1)
        this.globalBestMatrix = Arrays.copyOf(initialMatrix, initialMatrix.length);
        LayoutStats initSk = Evaluator.evaluate(globalBestMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
        LayoutStats initEn = Evaluator.evaluate(globalBestMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);
        this.globalBestScore = (initSk.getScore() * skRatio) + (initEn.getScore() * enRatio);
        this.dynamicThreshold = this.globalBestScore - 0.3;

        int cores = Runtime.getRuntime().availableProcessors() / 2;
        System.out.println("🔥 Štartujem optimizér na " + cores + " jadrách procesora...");
        System.out.println("📝 Pociatocny threshold na zapis do dennika " + JOURNAL_FILE + " je " + dynamicThreshold);

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

        LayoutStats currentSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
        LayoutStats currentEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);

        double enRatio = 1.0 - skRatio;
        double currentScore = (currentSkStats.getScore() * skRatio) + (currentEnStats.getScore() * enRatio);
        double localBestScore = currentScore;

        // 🌡️ OPTIMALIZOVANÉ PARAMETRE (Štartujeme nižšie, chladneme pomalšie)
        double temperature = 3.0; // 5.0 rychle prehladavanie, 3 stredne, 1.5 jemne prehladavanie okolo riesenia
        final double coolingRate = 0.000005; // 0.00001 bezne chladenie, 0,000005 pomale brusenie najdeneho riesenia

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
            LayoutStats nextSkStats = Evaluator.evaluate(currentMatrix, statsSK, weights, skMono, skBigram, skSkip, skTri);
            LayoutStats nextEnStats = Evaluator.evaluate(currentMatrix, statsEN, weights, enMono, enBigram, enSkip, enTri);

            double skScore = nextSkStats.getScore();
            double enScore = nextEnStats.getScore();
            double nextScore = AnalyzedLayout.combineScore(nextSkStats, nextEnStats, skRatio);

            if ((localEvaluations & 0x0FFFF) == 0) {
                // Živý výpis do konzoly
                System.out.printf("[Jadro %d] Počítam a žijem... Aktuálne skóre SK: %.4f EN: %.4f (Teplota: %.2f)%n", threadId, skScore, enScore, temperature);
            }

            // 📝 STRICT ZÁPIS DO DENNÍKA: Bez ohľadu na zlepšenie, ak spĺňa elitné bilingválne kritérium
            if (nextScore >= dynamicThreshold) {
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

//    private void checkAndSetGlobalBest(char[] matrix, double score, double sk, double en) {
//        synchronized (lock) {
//            if (score > globalBestScore) {
//                globalBestScore = score;
//                globalBestMatrix = Arrays.copyOf(matrix, matrix.length);
//                System.out.printf("🏆 Nový bilingválny rekord! Skóre: %.4f (SK: %.2f | EN: %.2f) [%s]%n",
//                        score, sk, en, formatMatrix(globalBestMatrix));
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

//    public static boolean isHumanFriendly(char[] matrix) {
//        for (int row = 0; row < 3; row++) {
//            int offset = row * 10;
//
//            for (int col = 0; col < 10; col++) {
//                char ch = matrix[offset + col];
//
//                // 1. Ak je to písmeno, nezaujíma nás to
//                if (ch >= 'A' && ch <= 'Z') continue;
//
//                // 2. VOĽNÉ STĹPCE: Tu symbol môže stáť úplne sám
//                if (col == 0 || col == 4 || col == 5 || col == 9) continue;
//
//                // 3. PODMIENENÉ STĹPCE
//                char left = matrix[offset + col - 1];
//                if (left < 'A' || left > 'Z') continue;
//                char right = matrix[offset + col + 1];
//                if (right < 'A' || right > 'Z') continue;
//                return false;
//            }
//        }
//        return true; // Všetky symboly sú buď na krajoch/stredoch, alebo v pároch
//    }

//    public static boolean isHumanFriendly(char[] matrix) {
//        for (int row = 0; row < 3; row++) {
//            int offset = row * 10;
//
//            for (int col = 0; col < 10; col++) {
//                char ch = matrix[offset + col];
//
//                // =========================================================================
//                // 🚨 ŠPECIÁLNA STRÁŽ PRE HOME ROW (Riadok 1) - Koncept prioritných klávesov
//                // =========================================================================
//                if (row == 1) {
//                    // Definuje odpad, ktorý na Home Row nechceme vidieť ani na sekundu
//                    boolean isTrashKey = (ch == 'Q' || ch == 'X' || ch == 'W' || ch == 'J' || ch == 'Z');
//
//                    if (col == 4 || col == 5) {
//                        // STRETCH STĹPCE: Povolené písmená (mimo trashu) ALEBO bodka a čiarka
//                        if (isTrashKey) return false;
//                        if (ch < 'A' || ch > 'Z') {
//                            if (ch != '.' && ch != ',') return false; // Slabé symboly do stredu nepustíme
//                        }
//                    } else {
//                        // HLAVNÉ POZÍCIE PRSTOV: Prísne iba čistá abeceda bez trashu a symbolov
//                        if (ch < 'A' || ch > 'Z' || isTrashKey) return false;
//                    }
//                    continue; // Home row vyriešená, ideme na ďalšiu klávesu
//                }
//
//                // =========================================================================
//                // 📐 PRAVIDLO PRE HORNÝ A SPODNÝ RIADOK (Riadky 0 a 2)
//                // =========================================================================
//                if (ch >= 'A' && ch <= 'Z') continue; // Písmená hore/dole neriešime
//                if (col == 0 || col == 4 || col == 5 || col == 9) continue; // Voľné stĺpce
//
//                // Podmienené stĺpce hore a dole: symbol musí mať vedľa seba parťáka
//                char left = matrix[offset + col - 1];
//                if (left < 'A' || left > 'Z') continue;
//
//                char right = matrix[offset + col + 1];
//                if (right < 'A' || right > 'Z') continue;
//
//                return false; // Osamotený symbol v riadkoch 0 alebo 2 -> KO
//            }
//        }
//        return true;
//    }

//    public static boolean isHumanFriendly(char[] matrix) {
//        int idx0 = -1, idx1 = -1, idx2 = -1, idx3 = -1;
//        int count = 0;
//
//        // 1. Single-pass zber indexov (automaticky zoradené 0-29)
//        for (int i = 0; i < KEY_COUNT; i++) {
//            char c = matrix[i];
//            if (c < 'A' || c > 'Z') {
//                if (count == 0) idx0 = i;
//                else if (count == 1) idx1 = i;
//                else if (count == 2) idx2 = i;
//                else if (count == 3) { idx3 = i; break; }
//                count++;
//            }
//        }
//
//        // =========================================================================
//        // 🛑 GLOBÁLNA STRÁŽ HOME ROW: Základný test pred všetkými módmi
//        // =========================================================================
//        // Vygenerujeme 30-bitovú masku polôh symbolov aktuálneho layoutu
//        long layoutSymbolsMask = (1L << idx0) | (1L << idx1) | (1L << idx2) | (1L << idx3);
//
//        // Porovnáme to s kartónom z KeyboardTopology (bity 11,12,13 a 16,17,18)
//        // Ak sa bity pretnú, symbol parazituje na jadrových prstoch -> OKAMŽITÁ POPRAVA
//        if ((layoutSymbolsMask & KeyboardTopology.FORBIDDEN_HOME_ROW_MASK) != 0) {
//            return false; // OKAMŽITÁ POPRAVA: Symbol parazituje na domovských prstoch
//        }
//
//        // =========================================================================
//        // ⚡ MÓD 3: STREDOVÉ STRETCHE (Symboly sú schované v zipse ukazovákov)
//        // =========================================================================
//        // Maska pre stredové stĺpce 4 a 5 (zapnuté bity pre indexy: 4, 5, 14, 15, 24, 25)
//        long centerColumnsMask = 0x300C030L;
//
//        // Bitový AND s negáciou masky. Ak vyjde 0, znamená to, že ŽIADNY symbol nevyliezol mimo stred.
//        if ((layoutSymbolsMask & ~centerColumnsMask) == 0) {
//            return true; // Sú kompletne v stredovom zipse? Majú absolútnu voľnosť, kašleme na symetriu!
//        }
//
//        // 2. Extrakcia rúk z POS_INFO (Maska 0x200 izoluje 9. bit)
//        int hand0 = KeyboardTopology.POS_INFO[idx0] & 0x200;
//        int hand1 = KeyboardTopology.POS_INFO[idx1] & 0x200;
//        int hand2 = KeyboardTopology.POS_INFO[idx2] & 0x200;
//        int hand3 = KeyboardTopology.POS_INFO[idx3] & 0x200;
//
//        // =========================================================================
//        // 👑 MÓD 1: JEDNORUČNÝ BLOK (Všetky symboly majú rovnakú ruku)
//        // =========================================================================
//        if (hand0 == hand1 && hand0 == hand2 && hand0 == hand3) {
//            return true; // Na jednej ruke mimo zakázanú Home Row? Absolútna sloboda.
//        }
//
//        // =========================================================================
//        // 📐 MÓD 2: OBOJRUČNÉ ROZLOŽENIE -> Čistá, nekompromisná zrkadlovosť
//        // =========================================================================
//        // Vytiahneme predpočítané finálne indexy zrkadiel (posun o 10 bitov a maska 0x1F)
//        int m0 = (KeyboardTopology.POS_INFO[idx0] >> 10) & 0x1F;
//        int m1 = (KeyboardTopology.POS_INFO[idx1] >> 10) & 0x1F;
//        int m2 = (KeyboardTopology.POS_INFO[idx2] >> 10) & 0x1F;
//        int m3 = (KeyboardTopology.POS_INFO[idx3] >> 10) & 0x1F;
//
//        return (m0 == idx1 || m0 == idx2 || m0 == idx3) &&
//                (m1 == idx0 || m1 == idx2 || m1 == idx3) &&
//                (m2 == idx0 || m2 == idx1 || m2 == idx3) &&
//                (m3 == idx0 || m3 == idx1 || m3 == idx2);
//    }

}
