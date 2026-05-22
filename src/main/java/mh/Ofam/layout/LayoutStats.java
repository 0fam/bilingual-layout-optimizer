package mh.Ofam.layout;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class LayoutStats {
    @Getter
    Layout layout;

    long[] rows = new long[3];
    long[] fingers = new long[10];
    long innerStretch = 0;

    long sfb = 0;
    long sfs = 0;
    long scissors = 0;
    long handAlternation = 0;

    long inwardRolls = 0;
    long outwardRolls = 0;
    long redirects = 0;
    long longOneHandedRuns = 0;

    // --- NOVÉ METRIKY PRE PROFI ANALÝZU ---
    long leftHandMonograms = 0;
    long rightHandMonograms = 0;
    long lsb = 0; // Lateral Stretch Bigrams (Kríženie so stredovým stĺpcom)
    long lhb = 0; // Lateral Hand Bigrams (Kríženie Malíček-Prstenník)
    double maxFingerLoadPct = 0.0;

    // Mapy na sledovanie konkrétnych problémových slov
    Map<String, Integer> sfbWordsMap = new HashMap<>();
    Map<String, Integer> longRunWordsMap = new HashMap<>();

    long totalMonograms = 0;
    long totalBigrams = 0;
    long totalSkipgrams = 0;
    long totalTrigrams = 0;

    @Getter
    double balancedScore = 0.0;

    public static LayoutStats instance(TextStats textStats, Layout layout) {
        LayoutStats ls = new LayoutStats();
        ls.layout = layout;

        int errorCounter = 0;

        for (String word : textStats.wordFreqs.keySet()) { // pre kazde slovo
            for (int freq = 0; freq < textStats.wordFreqs.get(word); freq++) { // tolko krat, kolko krat to slovo sa vyskytuje

                List<KeyInfo> keys = new ArrayList<>();
                for (char ch : word.toCharArray()) {
                    if (layout.charMap.containsKey(ch)) {
                        keys.add(layout.charMap.get(ch));
                    } else {
                        if (errorCounter < 100) log.warn("Klavesa pre znak {} v layoute {} neexistuje", ch, layout.name);
                        errorCounter++;
                    }
                }

                int len = keys.size();
                if (len == 0) continue;

                boolean wordHadSfb = false;
                boolean wordHadLongRun = false;

                // --- 1. ÚROVEŇ: MONOGRAMY ---
                for (KeyInfo k : keys) {
                    ls.totalMonograms++;
                    ls.rows[k.row]++;
                    ls.fingers[k.finger.ordinal()]++;

                    if (k.hand == 'L') ls.leftHandMonograms++;
                    else ls.rightHandMonograms++;

                    if (k.col == 4 || k.col == 5) {
                        ls.innerStretch++;
                    }
                }

                // --- 2. ÚROVEŇ: BIGRAMY & SKIPGRAMY ---
                for (int i = 0; i < len - 1; i++) {
                    KeyInfo k1 = keys.get(i);
                    KeyInfo k2 = keys.get(i + 1);
                    ls.totalBigrams++;

                    if (k1.hand != k2.hand) {
                        ls.handAlternation++;
                    }

                    if (k1.hand == k2.hand) {
                        // SFB (Same Finger Bigram)
                        // OPRAVA: Pridané k1 != k2, aby sme netrestali dvojité stlačenie toho istého klávesu (SKB)
                        if (k1.finger == k2.finger && k1 != k2) {
                            ls.sfb++;
                            wordHadSfb = true;
                        }

                        // Scissors
                        if (Math.abs(k1.finger.ordinal() - k2.finger.ordinal()) == 1) {
                            if ((k1.row == 0 && k2.row == 2) || (k1.row == 2 && k2.row == 0)) {
                                ls.scissors++;
                            }
                        }

                        // LSB: Jeden prst je v stredovom stĺpci a druhý prst na tej istej ruke nie je
                        boolean k1Stretch = (k1.col == 4 || k1.col == 5);
                        boolean k2Stretch = (k2.col == 4 || k2.col == 5);
                        if (k1Stretch != k2Stretch && k1.finger != k2.finger) {
                            ls.lsb++;
                        }

                        // LHB: Trápenie šľachy medzi malíčkom a prstenníkom (stĺpce 0, 1 a 8, 9)
                        boolean k1PinkyRing = (k1.col == 0 || k1.col == 1 || k1.col == 8 || k1.col == 9);
                        boolean k2PinkyRing = (k2.col == 0 || k2.col == 1 || k2.col == 8 || k2.col == 9);
                        if (k1PinkyRing && k2PinkyRing && k1.finger != k2.finger) {
                            ls.lhb++;
                        }
                    }

                    if (i < len - 2) {
                        KeyInfo k3 = keys.get(i + 2);
                        ls.totalSkipgrams++;
                        if (k1.hand == k3.hand && k1.finger == k3.finger && k1 != k3) {
                            ls.sfs++;
                        }
                    }
                }

                // --- 3. ÚROVEŇ: TRIGRAMY ---
                for (int i = 0; i < len - 2; i++) {
                    KeyInfo k1 = keys.get(i);
                    KeyInfo k2 = keys.get(i + 1);
                    KeyInfo k3 = keys.get(i + 2);
                    ls.totalTrigrams++;

                    if (k1.hand == k2.hand && k2.hand == k3.hand) {
                        int r1 = k1.finger.rank;
                        int r2 = k2.finger.rank;
                        int r3 = k3.finger.rank;

                        if (r1 < r2 && r2 < r3) {
                            ls.inwardRolls++;
                        } else if (r1 > r2 && r2 > r3) {
                            ls.outwardRolls++;
                        } else if ((r2 > r1 && r2 > r3) || (r2 < r1 && r2 < r3)) {
                            ls.redirects++;
                        }
                    }
                }

                // --- 4. ÚROVEŇ: JEDNORUČNÉ RUNS ---
                int currentRun = 1;
                char currentHand = keys.get(0).hand;
                for (int i = 1; i < len; i++) {
                    if (keys.get(i).hand == currentHand) {
                        currentRun++;
                    } else {
                        // Zvýšené na 5 znakov!
                        if (currentRun >= 5) {
                            ls.longOneHandedRuns++;
                            wordHadLongRun = true;
                        }
                        currentRun = 1;
                        currentHand = keys.get(i).hand;
                    }
                }
                // Zvýšené na 5 znakov!
                if (currentRun >= 5) {
                    ls.longOneHandedRuns++;
                    wordHadLongRun = true;
                }

                // Uložíme problémové slová do štatistiky
                if (wordHadSfb) {
                    ls.sfbWordsMap.put(word, ls.sfbWordsMap.getOrDefault(word, 0) + 1);
                }
                if (wordHadLongRun) {
                    ls.longRunWordsMap.put(word, ls.longRunWordsMap.getOrDefault(word, 0) + 1);
                }
            }
        }

        // --- VÝPOČET ZÁŤAŽE NAJHORŠIEHO PRSTA (MAX_FINGER_LOAD) ---
        // Spočítame skutočnú záťaž ukazovákov (základ + stretch) a porovnáme s ostatnými prstami
        long maxL = Math.max(Math.max(ls.fingers[0], ls.fingers[1]), Math.max(ls.fingers[2], ls.fingers[3] + ls.fingers[4]));
        long maxR = Math.max(Math.max(ls.fingers[9], ls.fingers[8]), Math.max(ls.fingers[7], ls.fingers[6] + ls.fingers[5]));
        long absoluteMaxFinger = Math.max(maxL, maxR);
        ls.maxFingerLoadPct = ls.totalMonograms == 0 ? 0 : (absoluteMaxFinger * 100.0) / ls.totalMonograms;

        ls.printLayoutReport(layout);

        return ls;
    }

    public void printLayoutReport(Layout layout) {
        if (totalMonograms == 0) {
            System.out.println("Prázdny alebo nevalidný korpus.");
            return;
        }

        System.out.println("======================================");
        System.out.println("   LAYOUT: " + layout.name.toUpperCase());
        System.out.println("======================================");
        System.out.println(layout.rows[0]);
        System.out.println(layout.rows[1]);
        System.out.println(layout.rows[2]);
        System.out.println();
        System.out.printf("Spracovaných platných znakov: %d\n", totalMonograms);
        System.out.printf("Spracovaných bigramov: %d | trigramov: %d\n", totalBigrams, totalTrigrams);
        System.out.println("--------------------------------------");

        System.out.println("\n[1] GLOBÁLNE ROZLOŽENIE ZÁŤAŽE");
        double leftPct = totalMonograms == 0 ? 0 : (leftHandMonograms * 100.0) / totalMonograms;
        double rightPct = totalMonograms == 0 ? 0 : (rightHandMonograms * 100.0) / totalMonograms;
        System.out.printf("  Ľavá ruka:  %6.2f%%\n", leftPct);
        System.out.printf("  Pravá ruka: %6.2f%%\n", rightPct);
        System.out.println();
        System.out.printf("  Top Row:    %6.2f%%\n", totalMonograms == 0 ? 0 : (rows[0] * 100.0) / totalMonograms);
        System.out.printf("  Home Row:   %6.2f%%  (Ideál: >60%%)\n", totalMonograms == 0 ? 0 : (rows[1] * 100.0) / totalMonograms);
        System.out.printf("  Bottom Row: %6.2f%%\n", totalMonograms == 0 ? 0 : (rows[2] * 100.0) / totalMonograms);

        System.out.println("\n[2] ZÁŤAŽ PRSTOV A BOČNÝ POHYB");
        System.out.printf("  Najviac namáhaný prst (Max Load): %6.2f%%\n", maxFingerLoadPct);
        System.out.printf("  Stredové stĺpce (Inner Stretch):  %6.2f%%\n\n", totalMonograms == 0 ? 0 : (innerStretch * 100.0) / totalMonograms);

        String[] fNames = {"Malíček", "Prstenník", "Prostredník", "Ukazovák", "Inner Stretch"};
        System.out.println("  ĽAVÁ RUKA:                               PRAVÁ RUKA:");
        for (int i = 0; i < 5; i++) {
            double lPct = totalMonograms == 0 ? 0 : (fingers[i] * 100.0) / totalMonograms;
            double rPct = totalMonograms == 0 ? 0 : (fingers[9 - i] * 100.0) / totalMonograms;
            System.out.printf("  %-15s: %5.2f%%      |      %-15s: %5.2f%%\n", fNames[i], lPct, fNames[i], rPct);
        }

        System.out.println("\n[3] RYTMUS A KRUŠNÉ MOMENTY (Bigramy & Skipgramy)");
        System.out.printf("  ❌ SFB (Same Finger Bigram):       %6.2f%%  (Kritické! Chceš <1.0%%)\n", totalBigrams == 0 ? 0 : (sfb * 100.0) / totalBigrams);
        System.out.printf("  ⚠️ SFS (Skipgram - Same Finger):   %6.2f%%\n", totalSkipgrams == 0 ? 0 : (sfs * 100.0) / totalSkipgrams);
        System.out.printf("  ✂️ Scissors (Nožnicový kríž):      %6.2f%%\n", totalBigrams == 0 ? 0 : (scissors * 100.0) / totalBigrams);
        System.out.printf("  ↔️ LSB (Bočné kríženie k stredu):  %6.2f%%\n", totalBigrams == 0 ? 0 : (lsb * 100.0) / totalBigrams);
        System.out.printf("  🤕 LHB (Kríženie Malíček-Prstenník):%5.2f%%\n", totalBigrams == 0 ? 0 : (lhb * 100.0) / totalBigrams);
        System.out.printf("  🟩 Alternácia rúk (Rhythm):        %6.2f%%\n", totalBigrams == 0 ? 0 : (handAlternation * 100.0) / totalBigrams);

        System.out.println("\n[4] POHYB V TRIGRAMOCH (Trigram Flow)");
        System.out.printf("  🔄 Valivé dnu (Inward Rolls):      %6.2f%%\n", totalTrigrams == 0 ? 0 : (inwardRolls * 100.0) / totalTrigrams);
        System.out.printf("  🔄 Valivé von (Outward Rolls):     %6.2f%%\n", totalTrigrams == 0 ? 0 : (outwardRolls * 100.0) / totalTrigrams);
        System.out.printf("  ❌ Zmeny smeru (Redirects):        %6.2f%%\n", totalTrigrams == 0 ? 0 : (redirects * 100.0) / totalTrigrams);
        System.out.printf("  🖐️ Dlhé jednoručné behy (>=5 ch):  %d x\n", longOneHandedRuns);

        System.out.println("\n[5] TOP 10 SLOV SPÔSOBUJÚCICH SFB (Kmitanie prsta)");
        printTopWords(sfbWordsMap, 10);

        System.out.println("\n[6] TOP 10 SLOV S DLHÝMI JEDNORUČNÝMI BEHMI (>=5 znaky na jednej ruke)");
        printTopWords(longRunWordsMap, 10);

        System.out.println("==================================================");
    }

    private static void printTopWords(Map<String, Integer> map, int limit) {
        List<Map.Entry<String, Integer>> sorted = map.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            System.out.println("  Žiadne výskyty.");
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            System.out.printf("  %2d. %-15s (%d x)\n", i + 1, entry.getKey(), entry.getValue());
        }
    }

    double getSfbPct() {
        return totalBigrams == 0 ? 0 : (sfb * 100.0) / totalBigrams;
    }

    double getSfsPct() {
        return totalSkipgrams == 0 ? 0 : (sfs * 100.0) / totalSkipgrams;
    }

    double getInnerStretchPct() {
        return totalMonograms == 0 ? 0 : (innerStretch * 100.0) / totalMonograms;
    }

    double getAlternationPct() {
        return totalBigrams == 0 ? 0 : (handAlternation * 100.0) / totalBigrams;
    }

    double getHomeRowPct() {
        return totalMonograms == 0 ? 0 : (rows[1] * 100.0) / totalMonograms;
    }

    long getLongOneHandedRuns() {
        return longOneHandedRuns;
    }

    public double getRunsPct() {
        return totalMonograms == 0 ? 0 : (longOneHandedRuns * 100.0) / totalMonograms;
    }

    double getScissorsPct() {
        return totalBigrams == 0 ? 0 : (scissors * 100.0) / totalBigrams;
    }

    double getRedirectsPct() {
        return totalTrigrams == 0 ? 0 : (redirects * 100.0) / totalTrigrams;
    }

    double getRollInPct() {
        return totalTrigrams == 0 ? 0 : (inwardRolls * 100.0) / totalTrigrams;
    }

    double getRollOutPct() {
        return totalTrigrams == 0 ? 0 : (outwardRolls * 100.0) / totalTrigrams;
    }

    public double getMaxFingerLoadPct() {
        return maxFingerLoadPct;
    }

    public double getLsbPct() {
        return totalBigrams == 0 ? 0 : (lsb * 100.0) / totalBigrams;
    }

    public double getLhbPct() {
        return totalBigrams == 0 ? 0 : (lhb * 100.0) / totalBigrams;
    }

}
