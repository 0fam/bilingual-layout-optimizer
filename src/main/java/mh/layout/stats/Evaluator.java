package mh.layout.stats;

import mh.layout.topology.KeyboardTopology;

public final class Evaluator {

    private Evaluator() {}

    public static LayoutStats evaluate(char[] matrix, NGramStats stats, Weights weights) {

        // --- MERANIE SUROVÝCH FREKVENCIÍ ---
        double stretchScore = 0;
        double homeRowScore = 0;
        double sfbScore = 0;
        double sfsScore = 0;
        double scissorsScore = 0;
        double lsbScore = 0;
        double lhbScore = 0;
        double altScore = 0;
        double rollInScore = 0;
        double rollOutScore = 0;
        double redirectsScore = 0;

        double[] fingerUsage = new double[8];


        // =========================================================================
        // 🎯 1. MONOGRAMY: Statický komfort pozícií a vyťaženie prstov
        // =========================================================================
        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            double freq = stats.getMonogramFreq(c);
            if (freq == 0) continue;

            // Výpočet mechanického naťahovania prstov (Stretch) podľa topológie
            stretchScore += freq * KeyboardTopology.POS_STRETCH_WEIGHT[i];

            // Sčítavanie skóre za udržanie frekventovaných klávesov na domovskom riadku
            if (KeyboardTopology.POS_TO_ROW[i] == KeyboardTopology.HOME_ROW) {
                homeRowScore += freq;
            }

            // Akumulácia celkového objemu práce pre konkrétny prst
            fingerUsage[KeyboardTopology.POS_TO_FINGER[i]] += freq;
        }


        // =========================================================================
        // ⚖️ 2. ANATOMICKÁ POISTKA: Percentuálne stropy vyťaženia prstov (Finger Load)
        // =========================================================================
        // Najprv spočítame celkový objem stlačení v tomto layoute, aby sme mali základ pre %
        double totalKeystrokes = 0;
        for (int f = 0; f < 8; f++) {
            totalKeystrokes += fingerUsage[f];
        }
        if (totalKeystrokes == 0) return new LayoutStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);


        // =========================================================================
        // 🧮 3. BIGRAMY A SKIPGRAMY: Maticový prechod klávesnice 30x30
        // =========================================================================
        for (int i = 0; i < 30; i++) {
            char c1 = matrix[i];
            int hand1   = KeyboardTopology.POS_TO_HAND[i];
            int row1    = KeyboardTopology.POS_TO_ROW[i];
            int finger1 = KeyboardTopology.POS_TO_FINGER[i];

            for (int j = 0; j < 30; j++) {
                if (i == j) continue;

                char c2 = matrix[j];
                double bFreq = stats.getBigramFreq(c1, c2);
                double sFreq = stats.getSkipgramFreq(c1, c2);

                // Ak táto dvojica znakov nemá v korpuse žiadnu frekvenciu, preskakujeme
                if (bFreq == 0 && sFreq == 0) continue;

                int hand2   = KeyboardTopology.POS_TO_HAND[j];
                int row2    = KeyboardTopology.POS_TO_ROW[j];
                int finger2 = KeyboardTopology.POS_TO_FINGER[j];

                // --- A. JEDNORUČNÉ METRIKY (Rovnaká ruka) ---
                if (hand1 == hand2) {

                    // SFB & SFS: Údery po sebe alebo cez jeden znak (sendvič) na tom istom prste
                    if (finger1 == finger2) {
                        if (bFreq > 0) sfbScore += bFreq;
                        if (sFreq > 0) sfsScore += sFreq;
                    } else {
                        // SCISSORS: Susedné prsty, kde jeden letí hore a druhý sa krčí dole (rozdiel riadkov = 2)
                        if (bFreq > 0 && Math.abs(finger1 - finger2) == 1 && Math.abs(row1 - row2) == 2) {
                            scissorsScore += bFreq;
                        }

                        // LHB: Simultánny tlak a únava na kritickú dvojicu malíček + prstenník
                        if (bFreq > 0 && ((finger1 == KeyboardTopology.L_PINKY && finger2 == KeyboardTopology.L_RING) ||
                                (finger1 == KeyboardTopology.L_RING   && finger2 == KeyboardTopology.L_PINKY) ||
                                (finger1 == KeyboardTopology.R_PINKY && finger2 == KeyboardTopology.R_RING) ||
                                (finger1 == KeyboardTopology.R_RING   && finger2 == KeyboardTopology.R_PINKY))) {
                            lhbScore += bFreq;
                        }

                        // VALENIE PRSTOV (Rolls): Smerový tok pohybu prstov dnu k palcu (In) alebo von k malíčku (Out)
                        if (bFreq > 0) {
                            if (hand1 == KeyboardTopology.LEFT_HAND) {
                                if (finger1 < finger2) rollInScore += bFreq;
                                else                   rollOutScore += bFreq;
                            } else {
                                if (finger1 > finger2) rollInScore += bFreq;
                                else                   rollOutScore += bFreq;
                            }
                        }
                    }
                }
                // --- B. OBOJRUČNÉ METRIKY (Ruky sa striedajú) ---
                else {
                    if (bFreq > 0) altScore += bFreq; // Čistá rytmická alternácia ľavá-pravá
                }

                // --- C. BOČNÉ STREČE (LSB) ---
                // Sleduje, či bigram zaťažuje vnútorné stĺpce ukazovákov (indexy 4, 14, 24 a 5, 15, 25)
                if (bFreq > 0 && (i == 4 || i == 14 || i == 24 || i == 5 || i == 15 || i == 25 ||
                        j == 4 || j == 14 || j == 24 || j == 5 || j == 15 || j == 25)) {
                    lsbScore += bFreq;
                }
            }
        }


        // =========================================================================
        // 🔄 4. TRIGRAMY: Smerové zmeny (Redirects / Pinbally) 30x30x30
        // =========================================================================
        for (int i = 0; i < 30; i++) {
            char c1 = matrix[i];
            int hand1   = KeyboardTopology.POS_TO_HAND[i];
            int finger1 = KeyboardTopology.POS_TO_FINGER[i];

            for (int j = 0; j < 30; j++) {
                if (i == j) continue;
                char c2 = matrix[j];
                int hand2   = KeyboardTopology.POS_TO_HAND[j];
                int finger2 = KeyboardTopology.POS_TO_FINGER[j];

                // Ak prvé dva údery nie sú na jednej ruke, čistý jednoručný trigram to už byť nemôže
                if (hand1 != hand2) continue;

                for (int k = 0; k < 30; k++) {
                    if (j == k || i == k) continue;
                    char c3 = matrix[k];

                    double trigramFreq = stats.getTrigramFreq(c1, c2, c3);
                    if (trigramFreq == 0) continue;

                    int hand3   = KeyboardTopology.POS_TO_HAND[k];
                    // Ak sa smer pohybu prstov medzi údermi zlomil (napr. prstenník -> ukazovák -> prostredník)
                    if (hand2 == hand3) {
                        int finger3 = KeyboardTopology.POS_TO_FINGER[k];
                        if ((finger1 < finger2 && finger2 > finger3) ||
                                (finger1 > finger2 && finger2 < finger3)) {
                            redirectsScore += trigramFreq;
                        }
                    }
                }
            }
        }


        // =========================================================================
        // 📈 5. NORMALIZÁCIA NA ČISTÉ PERCENTÁ & VÝPOČET KOEFICIENTOV
        // =========================================================================

// Prevod základných motorických pilierov na reálne percentá z textu
        double homeRowPct   = totalMonograms == 0 ? 0 : (homeRowScore * 100.0) / totalMonograms;
        double altPct       = totalBigrams == 0   ? 0 : (altScore * 100.0) / totalBigrams;
        double rollInPct    = totalTrigrams == 0  ? 0 : (rollInScore * 100.0) / totalTrigrams;
        double rollOutPct   = totalTrigrams == 0  ? 0 : (rollOutScore * 100.0) / totalTrigrams;

// Základné skóre komfortu (Čistá esenia pozitívnych vlastností rozloženia)
        double baseComfortScore = homeRowPct + altPct + (rollInPct * 0.5);

// Prevod negatívnych hriechov na percentá a bleskový výpočet cez našu metódu
        double sfbPct       = totalBigrams == 0   ? 0 : (sfbScore * 100.0) / totalBigrams;
        double sfsPct       = totalSkipgrams == 0 ? 0 : (sfsScore * 100.0) / totalSkipgrams;
        double stretchPct   = totalMonograms == 0 ? 0 : (stretchScore * 100.0) / totalMonograms;
        double scissorsPct  = totalBigrams == 0   ? 0 : (scissorsScore * 100.0) / totalBigrams;
        double redirectsPct = totalTrigrams == 0  ? 0 : (redirectsScore * 100.0) / totalTrigrams;
        double lsbPct       = totalBigrams == 0   ? 0 : (lsbScore * 100.0) / totalBigrams;
        double lhbPct       = totalBigrams == 0   ? 0 : (lhbScore * 100.0) / totalBigrams;
//        double runsPct      = totalMonograms == 0 ? 0 : (runsScore * 100.0) / totalMonograms; // runs nevyhodnocujeme do skore, bude iba v statistickom vypise

        // Pri stretchScore (priemerný stretch na jeden kláves) nepotrebujeme násobiť 100
        double avgStretch   = stretchScore / totalKeystrokes;

        // Spustenie jednotného filtra koeficientov (Všetky zóny záchrany a práva veta v jednom bloku)
        double sfbCoeff       = weights.calculateCoefficient(sfbPct, weights.getSfbGood(), weights.getSfbDead(), 1.10);
        double scissorsCoeff  = weights.calculateCoefficient(scissorsPct,  weights.getScissorsGood(),  weights.getScissorsDead(),  1.05);
        double redirectsCoeff = weights.calculateCoefficient(redirectsPct, weights.getRedirectsGood(), weights.getRedirectsDead(), 1.05);
        double stretchCoeff   = weights.calculateCoefficient(avgStretch,   weights.getStretchGood(),   weights.getStretchDead(),   1.10);

        // ANATOMICKÉ STROPY PRSTOV: Využijú presne tú istú metódu!
        double fingerLoadCoeff = 1.0;
        for (int f = 0; f < 8; f++) {
            double fingerPercent = (fingerUsage[f] / totalKeystrokes) * 100.0;
            double maxAllowed = KeyboardTopology.FINGER_MAX_CEILING_PCT[f];

            // Ak prst prekročí strop o viac ako 5 %, naša metóda mu automaticky napáli 0.01 (Veto)
            double fCoeff = calculateCoefficient(fingerPercent, maxAllowed, maxAllowed + 5.0, 1.0);
            fingerLoadCoeff *= fCoeff;
        }


        // =========================================================================
        // 🏆 6. MULTIPLIKATÍVNE FINÁLNE SKÓRE
        // =========================================================================
        double poweredProduct = baseComfortScore
                * sfbCoeff
                * scissorsCoeff
                * redirectsCoeff
                * stretchCoeff
                * fingerLoadCoeff;

        double finalScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0; // toto je priemer n-rozmerneho telesa, namiesto objemu

        return new LayoutStats(
                sfbScore, sfsScore, stretchScore, scissorsScore, redirectsScore,
                0, lsbScore, lhbScore, homeRowScore, altScore, rollInScore, rollOutScore
        );
    }

}
