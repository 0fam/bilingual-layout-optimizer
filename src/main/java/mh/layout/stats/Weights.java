package mh.layout.stats;

import java.util.EnumMap;
import java.util.Map;

public final class Weights {

    // Vnútorná prepravka pre tri parametre každej metriky
        private record LimitParam(
                double goodEnough,
                double notEnough
    ) {
    }

    private final Map<MetricType, LimitParam> registry = new EnumMap<>(MetricType.class);

    public Weights() {

        // Pozitívne: safe = kedy začína bonus, dead = pod čo layout okamžite zabijeme
        registry.put(MetricType.HOME_ROW,  new LimitParam(65.0, 45.0)); // Chceme nad 65%, pod 45% je to smrť
//        registry.put(MetricType.ALT,       new LimitParam(60.0, 40.0)); // Chceme striedanie rúk nad 35%
//        registry.put(MetricType.ROLL_IN,   new LimitParam(15.0,  0.0));
//        registry.put(MetricType.ROLL_OUT,  new LimitParam(10.0,  0.0));

        // Negatívne: safe = do koľko % je to ideál, dead = kedy uplatňujeme PRÁVO VETA
        registry.put(MetricType.SFB,       new LimitParam(1.5,  5.0)); // SFB: Do 1.5% je super elitné, nad 5.0% je kŕč
        registry.put(MetricType.SFS,       new LimitParam(4.0,  9.0)); // SFS: Sendviče do 4.0% ruka zvládne, nad 9.0% už je to zlé
        registry.put(MetricType.SCISSORS,  new LimitParam(0.5,  3.0)); // Scissors: Brutálne prekríženie, nulová tolerancia, smrť na 3.0%
        registry.put(MetricType.REDIRECTS, new LimitParam(4.0, 15.0)); // Redirects: Pinball neničí šľachy, len brzdí. Do 4.0% ideál, smrť voľnejšie na 15.0%
//        registry.put(MetricType.STRETCH,   new LimitParam(1.2,  1.8)); // Priemerný stretch na kláves, touto metrikou som si nie moc isty... je to taky presnejsi home-row + zatazenie prstov, ale vlemi sa to kryje a je otazne ako to nastavit

        // TODO, pocitaj zatazenie nie prstov, ale stlpcov, prsty mame predsa priradene, potom vies z toho rychlo vypocitat aj stretch uplne deatilne
        // FIXME resp. pocitaj zatazenie jednotlivych klavesov a mas to uplne jednoznacne....

        // Každý prst dostane svoj vlastný, natvrdo zadefinovaný safe strop a bod smrti
        registry.put(MetricType.L_PINKY,  new LimitParam(8.0,  12.0)); // Malíčky: Ideál 0%, bezpečné do 9%, nad 12% je to invalidita
        registry.put(MetricType.L_RING,   new LimitParam(12.0, 16.0)); // Prstenníky: Spojené s malíčkom. Bezpečné do 20%, nad 24% je to invalidita
        registry.put(MetricType.L_MIDDLE, new LimitParam(15.0, 20.0)); // Prostredníky: Stabilné piliere. Safe do 22%, smrť na 26%
        registry.put(MetricType.L_INDEX,  new LimitParam(18.0, 24.0)); // Ukazováky: Najsilnejšie, ale majú aj stredové stĺpce. Safe do 26%, smrť na 30%
        registry.put(MetricType.R_INDEX,  new LimitParam(18.0, 24.0));
        registry.put(MetricType.R_MIDDLE, new LimitParam(15.0, 20.0));
        registry.put(MetricType.R_RING,   new LimitParam(12.0, 16.0));
        registry.put(MetricType.R_PINKY,  new LimitParam(8.0,  12.0));
    }

    public double calculateCoefficient(MetricType type, double actualValue) {
        LimitParam p = registry.get(type);
        if (p == null) {
            System.out.println("Neznámy typ metriky: " + type);
            return 1.0; // koeficient 1 nezmeni sucin
        }

        // vráti hodnotu: bonus (>1.0), záchrana (0.01 až 1.0) alebo veto (0.01)
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
        return registry.size();
    }

}
