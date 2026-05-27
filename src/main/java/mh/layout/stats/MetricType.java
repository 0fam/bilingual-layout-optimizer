package mh.layout.stats;

public enum MetricType {
    // 🔴 NEGATÍVNE METRIKY (Čím viac %, tým nižší koeficient / pád k smrti)
    SFB,           // Kmitanie toho istého prsta po sebe
    SFS,           // Sendviče (skipgramy na rovnakom prste)
    SCISSORS,      // Biomechanická penalizácia pozícií z Topology
    REDIRECTS,     // Zmeny smeru v trigramoch (pinbally)
    LSB,           // Lateral Stretch Bigram (vychýlenie ukazováka)
    LHB,           // Lateral Hand Bigram (únava malíček-prstenník)
    EFFORT,        // celkova namaha
    RUNS,          // single hand runs, slova na jednej ruke

    // 🟢 POZITÍVNE METRIKY (Čím viac %, tým vyšší koeficient / bonus)
    HOME_ROW,      // Odmena za udržanie prstov na základnom riadku
    ALTERNATION,   // Rytmika striedania rúk
    ROLL_IN,       // Valivé pohyby smerom k palcu
    ROLL_OUT,      // Valivé pohyby smerom von
}
