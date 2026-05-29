package mh.layout.evaluator;

import mh.layout.stats.MetricType;
import mh.layout.topology.Finger;

import java.util.EnumMap;
import java.util.Map;

public final class Weights {

    // Vnútorná prepravka pre tri parametre každej metriky
        private record LimitParam(
                double goodEnough,
                double notEnough
    ) {
    }

    private final Map<MetricType, LimitParam> metricRegistry = new EnumMap<>(MetricType.class);
    private final Map<Finger, LimitParam> fingerRegistry = new EnumMap<>(Finger.class);

    public Weights() {

        // Negatívne: safe = do koľko % je to ideál, dead = kedy uplatňujeme PRÁVO VETA
        metricRegistry.put(MetricType.SFB,       new LimitParam(1.5,  5.0)); // SFB: Do 1.5% je super elitné, nad 5.0% je kŕč
        metricRegistry.put(MetricType.SFS,       new LimitParam(4.0,  9.0)); // SFS: Sendviče do 4.0% ruka zvládne, nad 9.0% už je to zlé
        metricRegistry.put(MetricType.SCISSORS,  new LimitParam(0.5,  3.0)); // Scissors: Brutálne prekríženie, nulová tolerancia, smrť na 3.0%
        metricRegistry.put(MetricType.REDIRECTS, new LimitParam(4.0, 15.0)); // Redirects: Pinball neničí šľachy, len brzdí. Do 4.0% ideál, smrť voľnejšie na 15.0%
        metricRegistry.put(MetricType.LSB,       new LimitParam(1.0,   6.0)); // Lateral Stretch Bigram (vychýlenie ukazováka)
        metricRegistry.put(MetricType.LHB,       new LimitParam(1.0,   5.0)); // Lateral Hand Bigram (únava malíček-prstenník)

//        metricRegistry.put(MetricType.EFFORT,   new LimitParam(1.2,  1.8)); // Priemerný stretch na kláves, touto metrikou som si nie moc isty... je to taky presnejsi home-row + zatazenie prstov, ale vlemi sa to kryje a je otazne ako to nastavit

        // Pozitívne: safe = kedy začína bonus, dead = pod čo layout okamžite zabijeme
        metricRegistry.put(MetricType.HOME_ROW,    new LimitParam(65.0, 45.0)); // Chceme nad 65%, pod 45% je to smrť
//        metricRegistry.put(MetricType.ALTERNATION, new LimitParam(50.0, 35.0)); // len osobna preferencia, vyhadzujeme aby to zbytocne nenahanalo lahke body pre layouty. // Chceme striedanie rúk nad 35%. Skvelý balans medzi Rollmi a Ping-Pongom
//        metricRegistry.put(MetricType.ROLL_IN,     new LimitParam(15.0,  0.0));
//        metricRegistry.put(MetricType.ROLL_OUT,    new LimitParam(10.0,  0.0));

        // Každý prst dostane svoj vlastný, natvrdo zadefinovaný safe strop a bod smrti
        // stary Analyzer ma toto:
//        fingerRegistry.put(Finger.LP, new LimitParam(9.0,  12.0)); // Malíčky: Ideál 0%, bezpečné do 9%, nad 12% je to invalidita
//        fingerRegistry.put(Finger.LR, new LimitParam(20.0, 24.0)); // Prstenníky: Spojené s malíčkom. Bezpečné do 20%, nad 24% je to invalidita
//        fingerRegistry.put(Finger.LM, new LimitParam(22.0, 26.0)); // Prostredníky: Stabilné piliere. Safe do 22%, smrť na 26%
//        fingerRegistry.put(Finger.LI, new LimitParam(26.0, 30.0)); // Ukazováky: Najsilnejšie, ale majú aj stredové stĺpce. Safe do 26%, smrť na 30%
//        fingerRegistry.put(Finger.RI, new LimitParam(26.0, 30.0));
//        fingerRegistry.put(Finger.RM, new LimitParam(22.0, 26.0));
//        fingerRegistry.put(Finger.RR, new LimitParam(20.0, 24.0));
//        fingerRegistry.put(Finger.RP, new LimitParam(9.0,  12.0));
        // novy system navrhol toto, co je
        // Malíček (Optimum max 9% + 3% tlmič)
        fingerRegistry.put(Finger.LP, new LimitParam(9.0,  12.0));
        fingerRegistry.put(Finger.RP, new LimitParam(9.0,  12.0));
        // Prstenník (Optimum max 13% + 4% tlmič)
        fingerRegistry.put(Finger.LR, new LimitParam(13.0, 17.0));
        fingerRegistry.put(Finger.RR, new LimitParam(13.0, 17.0));
        // Prostredník (Optimum max 19% + 4% tlmič)
        fingerRegistry.put(Finger.LM, new LimitParam(19.0, 23.0));
        fingerRegistry.put(Finger.RM, new LimitParam(19.0, 23.0));
        // Ukazovák (Optimum max 22% + 4% tlmič)
        fingerRegistry.put(Finger.LI, new LimitParam(22.0, 26.0));
        fingerRegistry.put(Finger.RI, new LimitParam(22.0, 26.0));
        /*
        Prst, Biomechanické optimum (Svetový štandard)
        Malíček (Pinky),       7 % –  9 %
        Prstenník (Ring),     10 % – 13 %
        Prostredník (Middle), 16 % – 19 %
        Ukazovák (Index),     18 % – 22 %
         */
    }

    public double calculateCoefficient(MetricType type, double actualValue) {
        LimitParam p = metricRegistry.get(type);
        if (p == null) {
            // ziaden vypis kvoli vykonu
            return -1.0; // koeficient 1 nezmeni sucin
        }
        // vráti hodnotu: bonus (>1.0), záchrana (0.01 až 1.0) alebo veto (0.01)
        return getCoefficient(actualValue, p.goodEnough, p.notEnough);
    }

    public double calculateFingerCoefficient(Finger f, double actualValue) {
        LimitParam p = fingerRegistry.get(f);
        if (p == null) {
            // ziaden vypis kvoli vykonu
            return 1.0; // koeficient 1 nezmeni sucin
        }
        return getCoefficient(actualValue, p.goodEnough, p.notEnough);
    }

    private double getCoefficient(double value, double safeLimit, double deathLimit) {
        final double maxBonus = 0.10; // Bonus +0.1 (10%) za teoretickú dokonalosť

        if (safeLimit < deathLimit) {
            // TRESTNÉ METRIKY (Čím menej, tým lepšie - napr. SFB, Stretch) Automatický ideál = 0.0)
            if (value >= deathLimit) return 0.01; // Gilotína
            if (safeLimit <= 0) return maxBonus; // Plná odmena za dokonalosť

            if (value <= safeLimit) {
                // Zóna Elity: Lineárny bonus od 1.00 do 1.10
                // limit 5, value 1, bonus +0,8
                return 1.0 + maxBonus * (1.0 - (value / safeLimit));
            } else {
                // Zóna Kompromisu: Voľný pád z 1.00 na 0.01
                return 0.01 + 0.99 * ((deathLimit - value) / (deathLimit - safeLimit));
            }
        } else {
            // ODMENOVÉ METRIKY (Čím viac, tým lepšie - napr. Alternácia, HomeRow) Automatický ideál = 100.0)
            if (value <= deathLimit) return 0.01;
            if (safeLimit == 100.0) return maxBonus;
            if (value >= safeLimit) {
                // Zóna Elity: Lineárny bonus od 1.00 do 1.10
                return 1.0 + maxBonus * ((value - safeLimit) / (100.0 - safeLimit));
            } else {
                // Zóna Kompromisu: Voľný pád z 1.00 na 0.01
                return 0.01 + 0.99 * ((value - deathLimit) / (safeLimit - deathLimit));
            }
        }
    }

    public int size() {
        return metricRegistry.size();
    }

}
