package mh.Ofam.layout;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Weights {
    double sfb = 0;
    double sfs = 0;
    double stretch = 0;
    double alt = 0;
    double runs = 0;
    double scissors = 0;
    double redirects = 0;
    double rollIn = 0;
    double rollOut = 0;
}
