package mh.Ofam.layout;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Weights {
    double sfb = 0; // single finger bigrams
    double sfs = 0; // skip finger bigrams
    double stretch = 0;
    double alt = 0;
    double runs = 0;
    double scissors = 0;
    double redirects = 0;
    double rollIn = 0;
    double rollOut = 0;

    private double maxFinger = 0;  // Zabraňuje preťaženiu jedného prsta
    private double lsb = 0;        // Lateral Stretch Bigram
    private double lhb = 0;        // Lateral Hand Bigram (malíček-prstenník)
    private double homeRow = 0;    // Odmena za to, že ruky sedia doma

    double pinkyPenalty = 0;
    double ringPenalty = 0;
    double middlePenalty = 0;
    double indexPenalty = 0;
    double indexUnderloadPenalty = 0;
    double middleUnderloadPenalty = 0;

    public double totalWeight() {
        return sfb + sfs + stretch + alt + runs + scissors + redirects + rollIn + rollOut + maxFinger + lsb + lhb + homeRow + pinkyPenalty + ringPenalty + middlePenalty + indexPenalty + indexUnderloadPenalty + middleUnderloadPenalty;
    }
}
