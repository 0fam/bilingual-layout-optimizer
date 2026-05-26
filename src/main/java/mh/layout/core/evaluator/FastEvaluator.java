package mh.layout.core.evaluator;

import mh.layout.core.stats.FastLayoutStats;
import mh.layout.core.stats.NGramStats;
import mh.layout.core.topology.KeyboardTopology;

import java.util.Map;

public final class FastEvaluator {

    private FastEvaluator() {}

    public static FastLayoutStats evaluate(char[] matrix, NGramStats stats, Weights weights) {

        // Pomocné pole na akumuláciu záťaže pre všetkých 8 prstov
        double[] fingerUsage = new double[8];

        // --- EXEKÚCIA BIOMECHANICKÝCH BLOKOV ---

        // 1. Znaková úroveň (Monomery)
        double stretchScore = calculateStretchAndLoad(matrix, stats, fingerUsage);
        double homeRowScore = calculateHomeRowScore(matrix, stats);

        // 2. Anatomická úroveň (Zmena názvu na čistý LOAD)
        double fingerLoadScore = calculateFingerLoad(fingerUsage);

        // 3. N-gramová úroveň (Bigramy, trigramy a upravené behy)
        double sfsScore = 0;

        // Vyhodnotenie behov s miernejším logaritmickým/odmocninovým prístupom
        double runsScore = calculateMildRuns(matrix, stats);
        double redirectsScore = 0;

        // Vnútri metódy evaluate()...

        double[] bigramResults = new double[7];
        calculateBigramMetrics(matrix, stats, bigramResults);

        double sfbScore       = bigramResults[0];
        double scissorsScore  = bigramResults[1];
        double lsbScore       = bigramResults[2];
        double lhbScore       = bigramResults[3];
        double altScore       = bigramResults[4];
        double rollInScore    = bigramResults[5];
        double rollOutScore   = bigramResults[6];

        // --- VÝPOČET FINÁLNEHO ORTIEĽU ---
        double finalEliteScore = calculateFinalEliteScore(
                sfbScore, sfsScore, stretchScore, scissorsScore, redirectsScore,
                fingerLoadScore, lsbScore, lhbScore, homeRowScore, altScore,
                rollInScore, rollOutScore, runsScore, weights
        );

        return new FastLayoutStats(
                finalEliteScore, sfbScore, sfsScore, stretchScore, scissorsScore, redirectsScore,
                fingerLoadScore, lsbScore, lhbScore, homeRowScore, altScore, rollInScore, rollOutScore, runsScore
        );
    }

    // =========================================================================
    // 🔒 PRIVÁTNE PODMETÓDY (JIT Kompilátor ich natvrdo inline-ne pre maximálny výkon)
    // =========================================================================

    private static double calculateStretchAndLoad(char[] matrix, NGramStats stats, double[] fingerLoad) {
        double stretch = 0;
        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            double freq = stats.getMonogramFreq(c);
            if (freq == 0) continue;

            stretch += freq * KeyboardTopology.POS_STRETCH_WEIGHT[i];
            fingerLoad[KeyboardTopology.POS_TO_FINGER[i]] += freq;
        }
        return stretch;
    }

    private static double calculateHomeRowScore(char[] matrix, NGramStats stats) {
        double homeRow = 0;
        for (int i = 0; i < 30; i++) {
            if (KeyboardTopology.POS_TO_ROW[i] == KeyboardTopology.HOME_ROW) {
                homeRow += stats.getMonogramFreq(matrix[i]);
            }
        }
        return homeRow;
    }

    private static double calculateFingerLoad(double[] fingerUsage) {
        double maxLoad = 0;
        for (int f = 0; f < 8; f++) {
            double usage = fingerUsage[f];
            double penaltyFactor = 1.0;

            // Koeficienty rešpektujúce anatómiu nízkeho profilu Choc V1
            if (f == KeyboardTopology.L_PINKY  || f == KeyboardTopology.R_PINKY)  penaltyFactor = 2.0;
            if (f == KeyboardTopology.L_RING   || f == KeyboardTopology.R_RING)   penaltyFactor = 1.5;
            if (f == KeyboardTopology.L_MIDDLE || f == KeyboardTopology.R_MIDDLE) penaltyFactor = 1.1;

            double calculatedLoad = usage * penaltyFactor;
            if (calculatedLoad > maxLoad) {
                maxLoad = calculatedLoad;
            }
        }
        return maxLoad;
    }

    /**
     * Alternatívny, jemnejší výpočet jednoručných behov.
     * Namiesto lineárneho sčítania tlmíme frekvenciu odmocninou.
     */
    private static double calculateMildRuns(char[] matrix, NGramStats stats) {
        double runsScore = 0;

        // Mapovanie znakov na pozície (rýchly index pre 5-gramy)
        int[] charToPos = new int[256];
        for (int i = 0; i < 30; i++) {
            charToPos[matrix[i]] = i;
        }

        // Prechádzame len zaznamenané 5-gramy z mapy (brutálne rýchle O(N))
        for (Map.Entry<Long, Double> entry : stats.getAllRun5Grams().entrySet()) {
            long key = entry.getKey();
            double freq = entry.getValue();

            // Rozbalíme 5 znakov z long kľúča
            char c1 = (char) ((key >> 32) & 0xFF);
            char c2 = (char) ((key >> 24) & 0xFF);
            char c3 = (char) ((key >> 16) & 0xFF);
            char c4 = (char) ((key >> 8) & 0xFF);
            char c5 = (char) (key & 0xFF);

            // Zistíme ruky pre všetky 5 znaky
            int h1 = KeyboardTopology.POS_TO_HAND[charToPos[c1]];
            int h2 = KeyboardTopology.POS_TO_HAND[charToPos[c2]];
            int h3 = KeyboardTopology.POS_TO_HAND[charToPos[c3]];
            int h4 = KeyboardTopology.POS_TO_HAND[charToPos[c4]];
            int h5 = KeyboardTopology.POS_TO_HAND[charToPos[c5]];

            // Ak sú všetky na rovnakej ruke (čistý jednoručný beh)
            if (h1 == h2 && h2 == h3 && h3 == h4 && h4 == h5) {
                // Tlmenie: Použijeme odmocninu, čím masívne znížime dopad 8-násobného prekrývania
                runsScore += Math.sqrt(freq);
            }
        }
        return runsScore;
    }

    private static void calculateBigramMetrics(char[] matrix, NGramStats stats, double[] bigramResults) {
        // Indexy pre pole výsledkov, aby sme nemuseli vracať objekt
        // 0=sfb, 1=scissors, 2=lsb, 3=lhb, 4=alt, 5=rollIn, 6=rollOut

        for (int i = 0; i < 30; i++) {
            char c1 = matrix[i];
            int hand1   = KeyboardTopology.POS_TO_HAND[i];
            int row1    = KeyboardTopology.POS_TO_ROW[i];
            int finger1 = KeyboardTopology.POS_TO_FINGER[i];

            for (int j = 0; j < 30; j++) {
                if (i == j) continue; // Ten istý kláves dvakrát po sebe nie je bigram

                char c2 = matrix[j];
                double freq = stats.getBigramFreq(c1, c2);
                if (freq == 0) continue; // Ak sa bigram v texte nevyskytuje, ideme ďalej

                int hand2   = KeyboardTopology.POS_TO_HAND[j];
                int row2    = KeyboardTopology.POS_TO_ROW[j];
                int finger2 = KeyboardTopology.POS_TO_FINGER[j];

                // -----------------------------------------------------------------
                // A. JEDNORUČNÉ METRIKY (Písmená sa píšu na rovnakej ruke)
                // -----------------------------------------------------------------
                if (hand1 == hand2) {

                    // 1. SFB (Same Finger Bigram) - Rovnaký prst, dve rôzne klávesy
                    if (finger1 == finger2) {
                        bigramResults[0] += freq;
                    }
                    // Susedné prsty na rovnakej ruke
                    else if (Math.abs(finger1 - finger2) == 1) {
                        // 2. SCISSORS (Nožnice) - Jeden prst hore, susedný dole (rozdiel riadkov je 2)
                        if (Math.abs(row1 - row2) == 2) {
                            bigramResults[1] += freq;
                        }
                    }

                    // 3. LHB (Lateral Hand Bigram) - Napätie medzi prstenníkom a malíčkom na rovnakej ruke
                    if ((finger1 == KeyboardTopology.L_PINKY && finger2 == KeyboardTopology.L_RING) ||
                            (finger1 == KeyboardTopology.L_RING   && finger2 == KeyboardTopology.L_PINKY) ||
                            (finger1 == KeyboardTopology.R_PINKY && finger2 == KeyboardTopology.R_RING) ||
                            (finger1 == KeyboardTopology.R_RING   && finger2 == KeyboardTopology.R_PINKY)) {
                        bigramResults[3] += freq;
                    }

                    // 4. VALENIE PRSTOV (Rolls) - Prsty idú plynule za sebou
                    // Smer valenia závisí od toho, či ideme k palcu (In) alebo od palca (Out)
                    if (finger1 != finger2) {
                        if (hand1 == KeyboardTopology.LEFT_HAND) {
                            if (finger1 < finger2) bigramResults[5] += freq; // L_PINKY (0) -> L_INDEX (3) = Roll-In
                            else                   bigramResults[6] += freq; // L_INDEX (3) -> L_PINKY (0) = Roll-Out
                        } else {
                            if (finger1 > finger2) bigramResults[5] += freq; // R_PINKY (7) -> R_INDEX (4) = Roll-In
                            else                   bigramResults[6] += freq; // R_INDEX (4) -> R_PINKY (7) = Roll-Out
                        }
                    }
                }
                // -----------------------------------------------------------------
                // B. OBOJRUČNÉ METRIKY (Ruky sa striedajú)
                // -----------------------------------------------------------------
                else {
                    // 5. ALT (Alternation) - Čisté rytmické bubnovanie ľavá-pravá
                    bigramResults[4] += freq;
                }

                // 6. LSB (Lateral Stretch Bigram) - Výskyt na vnútorných stĺpcoch ukazovákov
                // Pozície: 4, 14, 24 (ľavý stred) a 5, 15, 25 (pravý stred)
                if (i == 4 || i == 14 || i == 24 || i == 5 || i == 15 || i == 25 ||
                        j == 4 || j == 14 || j == 24 || j == 5 || j == 15 || j == 25) {
                    bigramResults[2] += freq;
                }
            }
        }
    }

    private static double calculateFinalEliteScore(
            double sfb, double sfs, double stretch, double scissors, double redirects,
            double fingerLoad, double lsb, double lhb, double homeRow, double alt,
            double rollIn, double rollOut, double runs, Weights w) {

        return (homeRow * w.getHomeRow())
                - (stretch * w.getStretch())
                - (fingerLoad * w.getFingerLoad())
                - (runs * w.getRuns());
    }
}
