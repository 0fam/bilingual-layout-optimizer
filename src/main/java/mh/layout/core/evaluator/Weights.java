package mh.layout.core.evaluator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public final class Weights {

    // --- TRESTNÉ METRIKY (Čím menej, tým lepšie) ---
    @Builder.Default private double sfb = 1.0;       // Kmitanie toho istého prsta po sebe
    @Builder.Default private double sfs = 1.0;       // Sendviče (skipgramy na rovnakom prste)
    @Builder.Default private double stretch = 1.0;   // Biomechanická penalizácia pozícií z Topology
    @Builder.Default private double scissors = 1.0;  // Nožnicové prekríženie prstov
    @Builder.Default private double redirects = 1.0; // Zmeny smeru v trigramoch (pinbally)

    // --- ANATOMICKÉ UKAZOVATELE (Ochrana rúk) ---
    @Builder.Default private double fingerLoad = 1.0;
    @Builder.Default private double lsb = 1.0;         // Lateral Stretch Bigram (vychýlenie ukazováka)
    @Builder.Default private double lhb = 1.0;         // Lateral Hand Bigram (únava malíček-prstenník)

    // --- ODMENOVÉ METRIKY (Čím viac, tým lepšie) ---
    @Builder.Default private double homeRow = 1.0;     // Odmena za udržanie prstov na základnom riadku
    @Builder.Default private double alt = 1.0;         // Rytmika striedania rúk
    @Builder.Default private double rollIn = 1.0;      // Valivé pohyby smerom k palcu
    @Builder.Default private double rollOut = 1.0;     // Valivé pohyby smerom von
    @Builder.Default private double runs = 1.0;        // Penalizácia/odmena za dlhé jednoručné sekvencie

    /**
     * Presný matematický súčet všetkých aktívnych váh.
     * Použijeme ho pri normalizácii výsledného skóre.
     */
    public double totalWeight() {
        return sfb + sfs + stretch + scissors + redirects + fingerLoad + lsb + lhb + homeRow + alt + rollIn + rollOut + runs;
    }
}
