package mh.Ofam.layout;

import lombok.Getter;
import mh.optimizer.Weights;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LayoutStats {
    @Getter
    Layout layout;

    long[] rows = new long[3];
    long[] fingers = new long[10];

    // 🔥 ZMENA: Zmenené na double, aby sme mohli sčítavať desatinné indexy kŕčovitosti
    double innerStretchL = 0;
    double innerStretchR = 0;

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

    // --- ZÁŤAŽ INDIVIDUÁLNYCH PRSTOV (Anatomické limity) ---
    double lPinky = 0;
    double lRing = 0;
    double lMiddle = 0;
    double lIndex = 0;

    double rPinky = 0;
    double rRing = 0;
    double rMiddle = 0;
    double rIndex = 0;

    // Mapy na sledovanie konkrétnych problémových slov
    Map<String, Long> sfbWordsMap;
    Map<String, Long> longRunWordsMap;
    Map<String, Long> innerStretchWordsMap;

    long totalMonograms = 0;
    long totalBigrams = 0;
    long totalSkipgrams = 0;
    long totalTrigrams = 0;

    @Getter
    double balancedScore = 0.0;
    @Getter
    double fingerPainScore = 0.0;
    @Getter
    double eliteScore = 0.0;

    public static LayoutStats instance(FastWord[] fastCorpus, Layout layout, Weights weights, boolean silent) {
        LayoutStats ls = new LayoutStats();
        ls.layout = layout;

        if (!silent) {
            ls.sfbWordsMap = new HashMap<>();
            ls.longRunWordsMap = new HashMap<>();
            ls.innerStretchWordsMap = new HashMap<>();
        }

        // --- MAGICKÝ TRIK: O(1) Lookup namiesto HashMapy ---
        // ASCII a bežné znaky sa zmestia do indexu 1024 (pre istotu)
        KeyInfo[] fastLookup = new KeyInfo[1024];
        for (Map.Entry<Character, KeyInfo> entry : layout.charMap.entrySet()) {
            char ch = entry.getKey();
            if (ch < fastLookup.length) {
                fastLookup[ch] = entry.getValue();
            }
        }

        // Vyrobíme si jedno pole, ktoré budeme neustále recyklovať. Žiadne 'new' v cykle!
        KeyInfo[] keys = new KeyInfo[100]; // 100 znakov stačí na akékoľvek slovo

        for (FastWord fw : fastCorpus) {
            if (fw.len == 0 || fw.len > 100) continue;

            boolean validWord = true;

            for (int i = 0; i < fw.len; i++) {
                char ch = fw.chars[i];

                // Žiadne .get(ch), len priamy skok do pamäte!
                KeyInfo info = ch < fastLookup.length ? fastLookup[ch] : null;

                if (info != null) {
                    keys[i] = info;
                } else {
                    validWord = false;
                    break; // Ak nepozná znak, preskočí slovo (bez logovania v hot path)
                }
            }
            if (!validWord) continue;

            boolean wordHadSfb = false;
            boolean wordHadLongRun = false;
            boolean hasInnerStretch = false;

            // --- 1. ÚROVEŇ: MONOGRAMY ---
            // OPRAVA: Používame fw.len a fw.freq
            for (int i = 0; i < fw.len; i++) {
                KeyInfo k = keys[i];

                ls.totalMonograms += fw.freq;
                ls.rows[k.row] += fw.freq;
                ls.fingers[k.finger.ordinal()] += fw.freq;

                if (k.hand == 'L') ls.leftHandMonograms += fw.freq;
                else ls.rightHandMonograms += fw.freq;

                // 🔥 ZMENA: Dynamický asymetrický výpočet kŕčovitosti stretchu podľa riadku
                if (k.col == 4) {
                    double weight = 0.1; // Home stretch (predvolený - takmer bez trestu)
                    if (k.row == 0) weight = 2.0;      // Top stretch = SMRŤ
                    else if (k.row == 2) weight = 1.0; // Bottom stretch = nepríjemné

                    ls.innerStretchL += fw.freq * weight;
                    hasInnerStretch = true;
                } else if (k.col == 5) {
                    double weight = 0.1; // Home stretch
                    if (k.row == 0) weight = 2.0;      // Top stretch = SMRŤ
                    else if (k.row == 2) weight = 1.0; // Bottom stretch

                    ls.innerStretchR += fw.freq * weight;
                    hasInnerStretch = true;
                }
            }

            // --- 2. ÚROVEŇ: BIGRAMY & SKIPGRAMY ---
            for (int i = 0; i < fw.len - 1; i++) {
                KeyInfo k1 = keys[i];
                KeyInfo k2 = keys[i + 1];
                ls.totalBigrams += fw.freq;

                if (k1.hand != k2.hand) {
                    ls.handAlternation += fw.freq;
                }

                if (k1.hand == k2.hand) {
                    // SFB (Same Finger Bigram)
                    if (k1.finger == k2.finger && k1 != k2) {
                        ls.sfb += fw.freq;
                        wordHadSfb = true;
                    }

                    // Scissors
                    if (Math.abs(k1.finger.ordinal() - k2.finger.ordinal()) == 1) {
                        if ((k1.row == 0 && k2.row == 2) || (k1.row == 2 && k2.row == 0)) {
                            ls.scissors += fw.freq;
                        }
                    }

                    // LSB: Jeden prst je v stredovom stĺpci a druhý prst na tej istej ruke nie je
                    boolean k1Stretch = (k1.col == 4 || k1.col == 5);
                    boolean k2Stretch = (k2.col == 4 || k2.col == 5);
                    if (k1Stretch != k2Stretch && k1.finger != k2.finger) {
                        ls.lsb += fw.freq;
                    }

                    // LHB: Trápenie šľachy medzi malíčkom a prstenníkom (stĺpce 0, 1 a 8, 9)
                    boolean k1PinkyRing = (k1.col == 0 || k1.col == 1 || k1.col == 8 || k1.col == 9);
                    boolean k2PinkyRing = (k2.col == 0 || k2.col == 1 || k2.col == 8 || k2.col == 9);
                    if (k1PinkyRing && k2PinkyRing && k1.finger != k2.finger) {
                        ls.lhb += fw.freq;
                    }
                }

                if (i < fw.len - 2) {
                    KeyInfo k3 = keys[i + 2];
                    ls.totalSkipgrams += fw.freq;
                    if (k1.hand == k3.hand && k1.finger == k3.finger && k1 != k3) {
                        ls.sfs += fw.freq;
                    }
                }
            }

            // --- 3. ÚROVEŇ: TRIGRAMY ---
            for (int i = 0; i < fw.len - 2; i++) {
                KeyInfo k1 = keys[i];
                KeyInfo k2 = keys[i + 1];
                KeyInfo k3 = keys[i + 2];
                ls.totalTrigrams += fw.freq;

                if (k1.hand == k2.hand && k2.hand == k3.hand) {
                    int r1 = k1.finger.rank;
                    int r2 = k2.finger.rank;
                    int r3 = k3.finger.rank;

                    if (r1 < r2 && r2 < r3) {
                        ls.inwardRolls += fw.freq;
                    } else if (r1 > r2 && r2 > r3) {
                        ls.outwardRolls += fw.freq;
                    } else if ((r2 > r1 && r2 > r3) || (r2 < r1 && r2 < r3)) {
                        ls.redirects += fw.freq;
                    }
                }
            }

            // --- 4. ÚROVEŇ: JEDNORUČNÉ RUNS ---
            int currentRun = 1;
            char currentHand = keys[0].hand;
            for (int i = 1; i < fw.len; i++) {
                if (keys[i].hand == currentHand) {
                    currentRun++;
                } else {
                    if (currentRun >= 5) {
                        ls.longOneHandedRuns += fw.freq;
                        wordHadLongRun = true;
                    }
                    currentRun = 1;
                    currentHand = keys[i].hand;
                }
            }
            if (currentRun >= 5) {
                ls.longOneHandedRuns += fw.freq;
                wordHadLongRun = true;
            }

            // --- MAPY PRE TOP 10 SLOV ---
            // OPRAVA: Tu vytvárame String z poľa chars len vtedy, ak potrebujeme logovať
            if (!silent) {
                String wordStr = new String(fw.chars);
                // Pretypovanie fw.freq na int, keďže mapa akceptuje <String, Integer>
                long mapFreq = fw.freq;
                if (wordHadSfb) ls.sfbWordsMap.put(wordStr, mapFreq);
                if (wordHadLongRun) ls.longRunWordsMap.put(wordStr, mapFreq);
                if (hasInnerStretch) ls.innerStretchWordsMap.put(wordStr, mapFreq);
            }
        }

        // Výpočet percent (ostal nezmenený)
        double total = ls.totalMonograms == 0 ? 1 : (double) ls.totalMonograms;
        ls.lPinky = (ls.fingers[0] * 100.0) / total;
        ls.lRing = (ls.fingers[1] * 100.0) / total;
        ls.lMiddle = (ls.fingers[2] * 100.0) / total;
        ls.lIndex = ((ls.fingers[3] + ls.fingers[4]) * 100.0) / total;
        ls.rPinky = (ls.fingers[9] * 100.0) / total;
        ls.rRing = (ls.fingers[8] * 100.0) / total;
        ls.rMiddle = (ls.fingers[7] * 100.0) / total;
        ls.rIndex = ((ls.fingers[6] + ls.fingers[5]) * 100.0) / total;

        ls.computeGlobalEliteScore(weights);
        if (!silent) ls.printLayoutReport(layout);

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
        layout.print();
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
        // 🔥 ZMENA: Premenovali sme popisy, aby bolo jasné, že ide o index zaťaženosti (vplyv riadkov)
        System.out.printf("  Inner Stretch Left (Weighted Index) :  %6.2f%%\n", totalMonograms == 0 ? 0 : (innerStretchL * 100.0) / totalMonograms);
        System.out.printf("  Inner Stretch Right (Weighted Index):  %6.2f%%\n\n", totalMonograms == 0 ? 0 : (innerStretchR * 100.0) / totalMonograms);

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

        System.out.println("\n[7] TOP 10 SLOV SO StretchOM (Použitie stredových stĺpcov)");
        printTopWords(innerStretchWordsMap, 10);

        System.out.println("==================================================");
    }

    private static void printTopWords(Map<String, Long> map, int limit) {
        List<Map.Entry<String, Long>> sorted = map.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            System.out.println("  Žiadne výskyty.");
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            System.out.printf("  %2d. %-15s (%d x)\n", i + 1, entry.getKey(), entry.getValue());
        }
    }

    double getSfbPct() {
        return totalBigrams == 0 ? 0 : (sfb * 100.0) / totalBigrams;
    }

    double getSfsPct() {
        return totalSkipgrams == 0 ? 0 : (sfs * 100.0) / totalSkipgrams;
    }

    double getInnerStretchPctL() {
        return totalMonograms == 0 ? 0 : (innerStretchL * 100.0) / totalMonograms;
    }

    double getInnerStretchPctR() {
        return totalMonograms == 0 ? 0 : (innerStretchR * 100.0) / totalMonograms;
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

    public double getLsbPct() {
        return totalBigrams == 0 ? 0 : (lsb * 100.0) / totalBigrams;
    }

    public double getLhbPct() {
        return totalBigrams == 0 ? 0 : (lhb * 100.0) / totalBigrams;
    }

    // --- ELITNÁ ŠKÁLOVACIA FUNKCIA S MIKRO-BONUSOM (Automatický ideál) ---
    // Parametre: hodnota, bezpečná zóna (skóre 1.0), gilotína (skóre 0.01)
    private double getEliteScore(double value, double safeLimit, double deathLimit) {
        double maxBonus = 1.10; // Bonus +0.1 (10%) za teoretickú dokonalosť

        if (safeLimit < deathLimit) {
            // TRESTNÉ METRIKY (Čím menej, tým lepšie - napr. SFB, Stretch) Automatický ideál = 0.0)
            double absoluteIdeal = 0.0;

            if (value >= deathLimit) return 0.01; // Gilotína
            if (value <= absoluteIdeal) return maxBonus; // Plná odmena za dokonalosť

            if (value <= safeLimit) {
                // Zóna Elity: Lineárny bonus od 1.00 do 1.10
                return 1.00 + (maxBonus - 1.00) * ((safeLimit - value) / (safeLimit - absoluteIdeal));
            } else {
                // Zóna Kompromisu: Voľný pád z 1.00 na 0.01
                return 0.01 + 0.99 * ((deathLimit - value) / (deathLimit - safeLimit));
            }
        } else {
            // ODMENOVÉ METRIKY (Čím viac, tým lepšie - napr. Alternácia, HomeRow) Automatický ideál = 100.0)
            double absoluteIdeal = 100.0;

            if (value <= deathLimit) return 0.01;
            if (value >= absoluteIdeal) return maxBonus;

            if (value >= safeLimit) {
                // Zóna Elity: Lineárny bonus od 1.00 do 1.10
                return 1.00 + (maxBonus - 1.00) * ((value - safeLimit) / (absoluteIdeal - safeLimit));
            } else {
                // Zóna Kompromisu: Voľný pád z 1.00 na 0.01
                return 0.01 + 0.99 * ((value - deathLimit) / (safeLimit - deathLimit));
            }
        }
    }

    public double computeGlobalEliteScore(Weights weights) {
        // Zisťujeme celkovú váhu pre geometrický priemer
        double totalWeight = weights.totalWeight();
        // Ochrana pred delením nulou (ak by si náhodou vypol úplne všetky váhy na 0.0)
        if (totalWeight == 0) return 0.0;

        // --- TRESTY (hodnota, SAFE_LIMIT, SMRŤ) ---
        // SFB: Do 1.5% je super elitné, nad 5.0% je kŕč
        double sfbScore       = getEliteScore(getSfbPct(), 1.5, 5.0);
        // SFS: Sendviče do 4.0% ruka zvládne, nad 9.0% už je to zlé
        double sfsScore       = getEliteScore(getSfsPct(), 4.0, 9.0);

        // 🔥 Vďaka tomu, že getInnerStretchPctL() a R() teraz pracujú s double váhami,
        // táto metóda automaticky prevezme nový sprísnený index nepohodlia!
        double stretchScore   = getEliteScore(getInnerStretchPctL() + getInnerStretchPctR(), 6.0, 15.0);
        // Scissors: Brutálne prekríženie, nulová tolerancia, smrť na 3.0%
        double scissorsScore  = getEliteScore(getScissorsPct(), 0.5, 3.0);
        // Redirects: Pinball neničí šľachy, len brzdí. Do 4.0% ideál, smrť voľnejšie na 15.0%
        double redirectsScore = getEliteScore(getRedirectsPct(), 4.0, 15.0);

// --- ZÁŤAŽ INDIVIDUÁLNYCH PRSTOV (Čisté stropy, preťažený prst zhodí layout) ---
        double total = totalMonograms == 0 ? 1 : (double) totalMonograms;

        double lPinky = (fingers[0] * 100.0) / total;
        double lRing = (fingers[1] * 100.0) / total;
        double lMiddle = (fingers[2] * 100.0) / total;
        double lIndex = ((fingers[3] + fingers[4]) * 100.0) / total;

        double rPinky = (fingers[9] * 100.0) / total;
        double rRing = (fingers[8] * 100.0) / total;
        double rMiddle = (fingers[7] * 100.0) / total;
        double rIndex = ((fingers[6] + fingers[5]) * 100.0) / total;

        // Malíčky: Ideál 0%, bezpečné do 9%, nad 12% je to invalidita
        double lPinkyScore = getEliteScore(lPinky, 9.0, 12.0);
        double rPinkyScore = getEliteScore(rPinky, 9.0, 12.0);
        // Malíčky: Ideál 0%, bezpečné do 9%, nad 12% je to invalidita
        double lRingScore = getEliteScore(lRing, 20.0, 24.0);
        double rRingScore = getEliteScore(rRing, 20.0, 24.0);
        // Prostredníky: Stabilné piliere. Safe do 22%, smrť na 26%
        double lMiddleScore = getEliteScore(lMiddle, 22.0, 26.0);
        double rMiddleScore = getEliteScore(rMiddle, 22.0, 26.0);
        // Ukazováky: Najsilnejšie, ale majú aj stredové stĺpce. Safe do 26%, smrť na 30%
        double lIndexScore = getEliteScore(lIndex, 26.0, 30.0);
        double rIndexScore = getEliteScore(rIndex, 26.0, 30.0);


        // Zjednotenie s optimizerom na čistý kumulatívny súčin prstov
        double cumulativeFingerScore = lPinkyScore * rPinkyScore * lRingScore * rRingScore * lMiddleScore * rMiddleScore * lIndexScore * rIndexScore;


        double lsbScore  = getEliteScore(getLsbPct(), 1.0, 6.0);
        double lhbScore  = getEliteScore(getLhbPct(), 1.0, 5.0);
        double runsScore = getEliteScore(getRunsPct(), 0.15, 1.20);

// --- ODMENY (hodnota, SAFE_LIMIT, SMRŤ) - Obrátené limity zabezpečia, že ideál bude 100.0 ---
        double altScore     = getEliteScore(getAlternationPct(), 60.0, 40.0);
        double rollInScore  = getEliteScore(getRollInPct(), 15.0, 0.0);
        double rollOutScore = getEliteScore(getRollOutPct(), 10.0, 0.0);
        double homeRowScore = getEliteScore(getHomeRowPct(), 65.0, 45.0);

// --- FINÁLNY VÝPOČET ---
        double poweredProduct =
                Math.pow(sfbScore, weights.getSfb()) *
                        Math.pow(sfsScore, weights.getSfs()) *
                        Math.pow(stretchScore, weights.getStretch()) *
                        Math.pow(altScore, weights.getAlt()) *
                        Math.pow(runsScore, weights.getRuns()) *
                        Math.pow(scissorsScore, weights.getScissors()) *
                        Math.pow(redirectsScore, weights.getRedirects()) *
                        Math.pow(rollInScore, weights.getRollIn()) *
                        Math.pow(rollOutScore, weights.getRollOut()) *
                        Math.pow(cumulativeFingerScore, weights.getFingerStats()) *
                        Math.pow(lsbScore, weights.getLsb()) *
                        Math.pow(lhbScore, weights.getLhb()) *
                        Math.pow(homeRowScore, weights.getHomeRow());

        eliteScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0; // toto je priemer n-rozmerneho telesa, namiesto objemu
//        eliteScore = poweredProduct * 10000;

        return eliteScore;
    }

}
