package mh.layout.core.evaluator;

import mh.layout.core.stats.FastLayoutStats;
import mh.layout.core.stats.NGramStats;
import mh.layout.core.topology.KeyboardTopology;

public final class FastEvaluator {

    private FastEvaluator() {}

    public static FastLayoutStats evaluate(char[] matrix, NGramStats stats, Weights weights) {

        // --- 1. ALOKÁCIA POMOCNÝCH STRUKTÚR PRE HORÚCU SLUČKU ---
        // Tieto polia slúžia ako akumulátory, vytvoríme ich tu raz, aby sme nealokovali v podmetódach
        double[] fingerUsage = new double[8];

        // --- 2. EXEKÚCIA BIOMECHANICKÝCH FÁZ (Krásne čitateľné bloky) ---

        // Fáza A: Výpočet statických hodnôt z jednotlivých klávesov (Monomery)
        double stretchScore = calculateMonomersAndFingerUsage(matrix, stats, fingerUsage);
        double homeRowScore = calculateHomeRowScore(matrix, stats);

        // Fáza B: Výpočet preťaženia konkrétnych prstov
        double fingerOverloadScore = calculateFingerOverload(fingerUsage);

        // Fázy C, D, E... (Sem neskôr zapojíme bigramy a trigramy: SFB, SFS, nožnice)
        double sfbScore = 0;
        double sfsScore = 0;
        double scissorsScore = 0;
        double redirectsScore = 0;
        double lsbScore = 0;
        double lhbScore = 0;
        double altScore = 0;
        double rollInScore = 0;
        double rollOutScore = 0;
        double runsScore = 0;

        // --- 3. FINÁLNY BILINGVÁLNY ORTIEĽ ---
        double finalEliteScore = calculateFinalEliteScore(
                sfbScore, sfsScore, stretchScore, scissorsScore, redirectsScore,
                fingerOverloadScore, lsbScore, lhbScore, homeRowScore, altScore,
                rollInScore, rollOutScore, runsScore, weights
        );

        return new FastLayoutStats(
                finalEliteScore, sfbScore, sfsScore, stretchScore, scissorsScore, redirectsScore,
                fingerOverloadScore, lsbScore, lhbScore, homeRowScore, altScore, rollInScore, rollOutScore, runsScore
        );
    }

    // =========================================================================
    // 🔒 PRIVÁTNE PODMETÓDY (JIT Kompilátor ich natvrdo inline-ne pre maximálny výkon)
    // =========================================================================

    private static double calculateMonomersAndFingerUsage(char[] matrix, NGramStats stats, double[] fingerUsage) {
        double stretch = 0;
        for (int i = 0; i < 30; i++) {
            char c = matrix[i];
            double freq = stats.getMonogramFreq(c);
            if (freq == 0) continue;

            stretch += freq * KeyboardTopology.POS_STRETCH_WEIGHT[i];

            int fingerId = KeyboardTopology.POS_TO_FINGER[i];
            fingerUsage[fingerId] += freq;
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

    private static double calculateFingerOverload(double[] fingerUsage) {
        double maxOverload = 0;
        for (int f = 0; f < 8; f++) {
            double usage = fingerUsage[f];
            double penaltyFactor = 1.0;

            // Koeficienty rešpektujúce anatómiu nízkeho profilu Choc V1
            if (f == KeyboardTopology.L_PINKY  || f == KeyboardTopology.R_PINKY)  penaltyFactor = 2.0;
            if (f == KeyboardTopology.L_RING   || f == KeyboardTopology.R_RING)   penaltyFactor = 1.5;
            if (f == KeyboardTopology.L_MIDDLE || f == KeyboardTopology.R_MIDDLE) penaltyFactor = 1.1;

            double calculatedLoad = usage * penaltyFactor;
            if (calculatedLoad > maxOverload) {
                maxOverload = calculatedLoad;
            }
        }
        return maxOverload;
    }

    private static double calculateFinalEliteScore(
            double sfb, double sfs, double stretch, double scissors, double redirects,
            double fingerOverload, double lsb, double lhb, double homeRow, double alt,
            double rollIn, double rollOut, double runs, Weights w) {

        // Tu bude tvoj kompletný vážený bilingválny vzorec
        return (homeRow * w.getHomeRow())
                - (stretch * w.getStretch())
                - (fingerOverload * w.getFingerLoad());
    }
}
