package mh.optimizer;

import mh.Ofam.helper.FileSystemHelper;
import mh.Ofam.layout.TextStats;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws URISyntaxException, IOException {
        System.out.println("=== INICIALIZÁCIA TOTEM LAYOUT OPTIMIZERA (N-GRAM ENGINE v2026) ===");

        // 1. Získaj svoje reálne dáta, ktoré ti už v IntelliJ fungovali
        // (Tieto metódy nižšie sú len príklad, nahraď ich svojím načítaním korpusov)
        Map<String, Long> skWordFreqs = loadYourExistingSlovakMap();
        Map<String, Long> enWordFreqs = loadYourExistingEnglishMap();

        if (skWordFreqs.isEmpty()) { // || enWordFreqs.isEmpty()) {
            System.out.println("⚠️ POZOR: Mapy korpusov sú prázdne. Nezabudni sem prepojiť svoj reálny parser!");
            return;
        }

        // 2. Štartovací layout (Musí mať presne 30 znakov pre Totem)
        // Predstavuje 3 riadky po 10 klávesov zľava doprava.
        // DÔLEŽITÉ: Uisti sa, že tento string obsahuje PRESNE tie isté znaky (vrátane diakritiky),
        // aké sa nachádzajú vo tvojich wordFreqs mapách, inak ich evaluátor preskočí.
//        String initialLayoutStr = "qdhgvwcuoyntrsm,peaibzlkf.j;'x"; //dof
        String initialLayoutStr = "zdjbg'ukoxntrmv.seaifclp,qhw;y"; //dof2770


        // 3. Odpal energetické jadro
        LayoutOptimizer optimizer = new LayoutOptimizer();
        optimizer.runOptimization(skWordFreqs, enWordFreqs, initialLayoutStr, 500_000, 4);
    }

    private static Map<String, Long> loadYourExistingSlovakMap() throws URISyntaxException, IOException {
        // Tu prepoj tvoj existujúci TextStats parser pre slovenčinu
        // Napr.: return myTextParser.getSlovakStats().wordFreqs;
        String korpusPath = "korpus.txt";
        String rawKorpus = FileSystemHelper.readKorpus(korpusPath);
        if (rawKorpus.strip().isEmpty()) {
            System.err.println("Chyba: Načítaný súbor s korpusom je prázdny!");
            return null;
        }
        TextStats textStats = TextStats.instance(rawKorpus);
        return textStats.getWordFreqs();
    }

    private static Map<String, Long> loadYourExistingEnglishMap() {
        // Tu prepoj tvoj existujúci TextStats parser pre angličtinu
        // Napr.: return myTextParser.getEnglishStats().wordFreqs;
        return new HashMap<>();
    }

}
