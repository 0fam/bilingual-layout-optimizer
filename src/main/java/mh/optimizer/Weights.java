package mh.optimizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Weights {

    // --- TRESTNÉ METRIKY (Čím menej, tým lepšie) ---
    @Builder.Default private double sfb = 1.0;       // Kmitanie toho istého prsta po sebe
    @Builder.Default private double sfs = 1.0;       // Sendviče (skipgramy na rovnakom prste)
    @Builder.Default private double stretch = 0.0;   // Inner Stretch (stredové stĺpce ukazovákov)
    @Builder.Default private double scissors = 1.0;  // Nožnicové prekríženie (napr. Top -> Bottom na susedných prstoch)
    @Builder.Default private double redirects = 1.0; // Pinbally / zmeny smeru v trigramoch

    // --- ANATOMICKÉ UKAZOVATELE (Bočné namáhanie a ochrana) ---
    @Builder.Default private double fingerStats = 1.0; // Globálna poistka proti preťaženiu najslabšieho prsta
    @Builder.Default private double lsb = 1.0;         // Lateral Stretch Bigram (vychýlenie ukazováka)
    @Builder.Default private double lhb = 1.0;         // Lateral Hand Bigram (únava malíček-prstenník)

    // --- ODMENOVÉ METRIKY (Čím viac, tým lepšie) ---
    @Builder.Default private double homeRow = 1.0;     // Odmena za udržanie prstov na základnom riadku
    @Builder.Default private double alt = 0.0;         // Striedanie rúk (rytmika)
    @Builder.Default private double rollIn = 0.0;      // Valivé pohyby smerom k palcu (Plynulosť)
    @Builder.Default private double rollOut = 0.0;     // Valivé pohyby smerom von
    @Builder.Default private double runs = 0.0;        // sekvencie pisane jednou rukou x-znakov za sebou

    /**
     * Presný matematický súčet len aktívnych váh.
     * Zabezpečuje, že geometrický priemer (n-rozmerná strana) bude dokonale vyvážený.
     */
    public double totalWeight() {
        return sfb + sfs + stretch + scissors + redirects + fingerStats + lsb + lhb + homeRow + alt + rollIn + rollOut + runs;
    }
}
