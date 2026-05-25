package mh.Ofam.layout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Weights {
    double sfb = 1; // single finger bigrams
    double sfs = 1; // skip finger bigrams
    double stretch = 1;
    double alt = 0;
    double runs = 1; // single hand runs (5 chars)
    double scissors = 1;
    double redirects = 1;
    double rollIn = 0;
    double rollOut = 0;

    private double fingerStats = 1;  // Zabraňuje preťaženiu prstov
    private double lsb = 1;        // Lateral Stretch Bigram
    private double lhb = 1;        // Lateral Hand Bigram (malíček-prstenník)
    private double homeRow = 1;    // Odmena za to, že ruky sedia doma

    double pinkyPenalty = 1;
    double ringPenalty = 1;
    double middlePenalty = 1;
    double indexPenalty = 1;
    double indexUnderloadPenalty = 0;
    double middleUnderloadPenalty = 0;

    public double totalWeight() {
        return sfb + sfs + stretch + alt + runs + scissors + redirects + rollIn + rollOut + fingerStats + lsb + lhb + homeRow + pinkyPenalty + ringPenalty + middlePenalty + indexPenalty + indexUnderloadPenalty + middleUnderloadPenalty;
    }
}
