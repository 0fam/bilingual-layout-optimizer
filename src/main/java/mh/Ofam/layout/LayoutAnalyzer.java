package mh.Ofam.layout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LayoutAnalyzer {

    enum Finger {
        L_PINKY(0), L_RING(1), L_MIDDLE(2), L_INDEX(3), L_INNER(4),
        R_INNER(5), R_INDEX(6), R_MIDDLE(7), R_RING(8), R_PINKY(9);

        final int rank;

        Finger(int col) {
            this.rank = col <= 4 ? col : 9 - col;
        }
    }

    static class KeyInfo {
        int row;
        int col;
        Finger finger;
        char hand;

        KeyInfo(int row, int col) {
            this.row = row;
            this.col = col;
            this.hand = col <= 4 ? 'L' : 'R';
            this.finger = Finger.values()[col];
        }
    }

    static class Layout {
        String name;
        Map<Character, KeyInfo> charMap = new HashMap<>();

        Layout(String name, String[] rows) {
            this.name = name;
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r].toLowerCase();
                for (int c = 0; c < row.length(); c++) {
                    char ch = row.charAt(c);
                    charMap.put(ch, new KeyInfo(r, c));
                }
            }
        }
    }

    // --- ŠTATISTICKÉ KONTAJNERY ---
    static class Stats {
        long totalMonograms = 0;
        long totalBigrams = 0;
        long totalSkipgrams = 0;
        long totalTrigrams = 0;

        long[] rows = new long[3];
        long[] fingers = new long[10];
        long innerStretch = 0;

        long sfb = 0;
        long sfs = 0;
        long scissors = 0;
        long handAlternation = 0;

        long inwardRolls = 0;
        long outwardRolls = 0;
        long redirects = 0;
        long longOneHandedRuns = 0;

        Map<Character, Long> charFreqs = new HashMap<>();
        // Mapy na sledovanie konkrétnych problémových slov
        Map<String, Integer> sfbWordsMap = new HashMap<>();
        Map<String, Integer> longRunWordsMap = new HashMap<>();
    }

    // --- WRAPPER PRE GLOBÁLNE ZORADZOVANIE A VÁHY ---
    static class AnalysisResult {
        String layoutName;
        Stats stats;
        double balancedScore = 0.0;

        AnalysisResult(String layoutName, Stats stats) {
            this.layoutName = layoutName;
            this.stats = stats;
        }

        String getLayoutName() {
            return layoutName;
        }

        double getSfbPct() {
            return stats.totalBigrams == 0 ? 0 : (stats.sfb * 100.0) / stats.totalBigrams;
        }

        double getSfsPct() {
            return stats.totalSkipgrams == 0 ? 0 : (stats.sfs * 100.0) / stats.totalSkipgrams;
        }

        double getInnerStretchPct() {
            return stats.totalMonograms == 0 ? 0 : (stats.innerStretch * 100.0) / stats.totalMonograms;
        }

        double getAlternationPct() {
            return stats.totalBigrams == 0 ? 0 : (stats.handAlternation * 100.0) / stats.totalBigrams;
        }

        double getHomeRowPct() {
            return stats.totalMonograms == 0 ? 0 : (stats.rows[1] * 100.0) / stats.totalMonograms;
        }

        long getLongOneHandedRuns() {
            return stats.longOneHandedRuns;
        }

        double getBalancedScore() {
            return balancedScore;
        }
    }

    public static Stats analyze(String text, Layout layout) {
        Stats s = new Stats();

        // <--- TU SA DEJE ODSTRÁNENIE DIAKRITIKY --->
        String cleanText = stripDiacritics(text).toLowerCase();

        // Rozdelenie na slová (odstránenie interpunkcie pre presnosť slov)
//        String[] words = cleanText.split("[^a-zA-Z0-9/.,;'-]+");
        String[] words = cleanText.split("[^a-z0-9']+");

        for (String word : words) {
            if (word.isEmpty()) continue;

            List<KeyInfo> keys = new ArrayList<>();
            for (char ch : word.toCharArray()) {
                if (layout.charMap.containsKey(ch)) {
                    keys.add(layout.charMap.get(ch));
                    // Tu pridáme počítanie samotného znaku:
                    s.charFreqs.put(ch, s.charFreqs.getOrDefault(ch, 0L) + 1L);
                }
            }

            int len = keys.size();
            if (len == 0) continue;

            boolean wordHadSfb = false;
            boolean wordHadLongRun = false;

            // --- 1. ÚROVEŇ: MONOGRAMY ---
            for (KeyInfo k : keys) {
                s.totalMonograms++;
                s.rows[k.row]++;
                s.fingers[k.finger.ordinal()]++;
                if (k.col == 4 || k.col == 5) {
                    s.innerStretch++;
                }
            }

            // --- 2. ÚROVEŇ: BIGRAMY & SKIPGRAMY ---
            for (int i = 0; i < len - 1; i++) {
                KeyInfo k1 = keys.get(i);
                KeyInfo k2 = keys.get(i + 1);
                s.totalBigrams++;

                if (k1.hand != k2.hand) {
                    s.handAlternation++;
                }

                if (k1.hand == k2.hand && k1.finger == k2.finger) {
                    s.sfb++;
                    wordHadSfb = true; // Zaznačíme, že toto slovo spôsobilo SFB
                }

                if (k1.hand == k2.hand && Math.abs(k1.finger.ordinal() - k2.finger.ordinal()) == 1) {
                    if ((k1.row == 0 && k2.row == 2) || (k1.row == 2 && k2.row == 0)) {
                        s.scissors++;
                    }
                }

                if (i < len - 2) {
                    KeyInfo k3 = keys.get(i + 2);
                    s.totalSkipgrams++;
                    if (k1.hand == k3.hand && k1.finger == k3.finger && k1 != k3) {
                        s.sfs++;
                    }
                }
            }

            // --- 3. ÚROVEŇ: TRIGRAMY ---
            for (int i = 0; i < len - 2; i++) {
                KeyInfo k1 = keys.get(i);
                KeyInfo k2 = keys.get(i + 1);
                KeyInfo k3 = keys.get(i + 2);
                s.totalTrigrams++;

                if (k1.hand == k2.hand && k2.hand == k3.hand) {
                    int r1 = k1.finger.rank;
                    int r2 = k2.finger.rank;
                    int r3 = k3.finger.rank;

                    if (r1 < r2 && r2 < r3) {
                        s.inwardRolls++;
                    } else if (r1 > r2 && r2 > r3) {
                        s.outwardRolls++;
                    } else if ((r2 > r1 && r2 > r3) || (r2 < r1 && r2 < r3)) {
                        s.redirects++;
                    }
                }
            }

            // --- 4. ÚROVEŇ: JEDNORUČNÉ RUNS ---
            int currentRun = 1;
            char currentHand = keys.get(0).hand;
            for (int i = 1; i < len; i++) {
                if (keys.get(i).hand == currentHand) {
                    currentRun++;
                } else {
                    if (currentRun >= 4) {
                        s.longOneHandedRuns++;
                        wordHadLongRun = true;
                    }
                    currentRun = 1;
                    currentHand = keys.get(i).hand;
                }
            }
            if (currentRun >= 4) {
                s.longOneHandedRuns++;
                wordHadLongRun = true;
            }

            // Uložíme problémové slová do štatistiky
            if (wordHadSfb) {
                s.sfbWordsMap.put(word, s.sfbWordsMap.getOrDefault(word, 0) + 1);
            }
            if (wordHadLongRun) {
                s.longRunWordsMap.put(word, s.longRunWordsMap.getOrDefault(word, 0) + 1);
            }
        }

        return s;
    }

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}");

    private static String stripDiacritics(String src) {
        if (src == null) return "";
        String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }

    public static void printReport(Stats s, String layoutName) {
        if (s.totalMonograms == 0) {
            System.out.println("Prázdny alebo nevalidný korpus.");
            return;
        }

        System.out.println("======================================");
        System.out.println("   LAYOUT: " + layoutName.toUpperCase());
        System.out.println("======================================");
        System.out.printf("Spracovaných platných znakov: %d\n", s.totalMonograms);
        System.out.printf("Spracovaných bigramov: %d | trigramov: %d\n", s.totalBigrams, s.totalTrigrams);
        System.out.println("--------------------------------------");

        System.out.println("\n[1] ZÁŤAŽ RIADKOV (Row Distribution)");
        System.out.printf("  Top Row:    %6.2f%%\n", (s.rows[0] * 100.0) / s.totalMonograms);
        System.out.printf("  Home Row:   %6.2f%%  (Ideál: >60%%)\n", (s.rows[1] * 100.0) / s.totalMonograms);
        System.out.printf("  Bottom Row: %6.2f%%\n", (s.rows[2] * 100.0) / s.totalMonograms);

        System.out.println("\n[2] PENALIZOVANÝ BOČNÝ POHYB (Inner Index Stretch)");
        System.out.printf("  Stredové stĺpce: %6.2f%% (Čím menej, tým lepšie)\n", (s.innerStretch * 100.0) / s.totalMonograms);

        System.out.println("\n[3] ZÁŤAŽ PRSTOV (Finger Balance)");
        String[] fNames = {"Malíček", "Prstenník", "Prostredník", "Ukazovák", "Inner Stretch"};
        System.out.println("  ĽAVÁ RUKA:                               PRAVÁ RUKA:");
        for (int i = 0; i < 5; i++) {
            double leftPct = (s.fingers[i] * 100.0) / s.totalMonograms;
            double rightPct = (s.fingers[9 - i] * 100.0) / s.totalMonograms;
            System.out.printf("  %-15s: %5.2f%%      |      %-15s: %5.2f%%\n", fNames[i], leftPct, fNames[i], rightPct);
        }

        System.out.println("\n[4] RYTMUS A KRUŠNÉ MOMENTY (Bigramy & Skipgramy)");
        System.out.printf("  ❌ SFB (Same Finger Bigram):       %6.2f%%  (Kritické! Chceš <1.0%%)\n", (s.sfb * 100.0) / s.totalBigrams);
        System.out.printf("  ⚠️ SFS (Skipgram - Same Finger):   %6.2f%%\n", (s.sfs * 100.0) / s.totalSkipgrams);
        System.out.printf("  ✂️ Scissors (Nožnicový kríž):      %6.2f%%\n", (s.scissors * 100.0) / s.totalBigrams);
        System.out.printf("  🟩 Alternácia rúk (Rhythm):        %6.2f%%\n", (s.handAlternation * 100.0) / s.totalBigrams);

        System.out.println("\n[5] POHYB V TRIGRAMOCH (Trigram Flow)");
        System.out.printf("  🔄 Valivé dnu (Inward Rolls):      %6.2f%%\n", (s.inwardRolls * 100.0) / s.totalTrigrams);
        System.out.printf("  🔄 Valivé von (Outward Rolls):     %6.2f%%\n", (s.outwardRolls * 100.0) / s.totalTrigrams);
        System.out.printf("  ❌ Zmeny smeru (Redirects):        %6.2f%%\n", (s.redirects * 100.0) / s.totalTrigrams);
        System.out.printf("  🖐️ Dlhé jednoručné behy (>=4 ch):  %d x\n", s.longOneHandedRuns);

        // --- NOVÉ SEKCE VÝPISU TOP SLOV ---
        System.out.println("\n[6] TOP 10 SLOV SPÔSOBUJÚCICH SFB (Kmitanie prsta)");
        printTopWords(s.sfbWordsMap, 10);

        System.out.println("\n[7] TOP 10 SLOV S DLHÝMI JEDNORUČNÝMI BEHMI (>=4 znaky na jednej ruke)");
        printTopWords(s.longRunWordsMap, 10);

        System.out.println("\n[8] FREKVENCIA JEDNOTLIVÝCH ZNAKOV (Od najpoužívanejšieho)");
        List<Map.Entry<Character, Long>> sortedChars = s.charFreqs.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toList());

        int count = 0;
        for (Map.Entry<Character, Long> entry : sortedChars) {
            double charPct = (entry.getValue() * 100.0) / s.totalMonograms;
            System.out.printf("  '%c' : %5.2f%% (%d x)", entry.getKey(), charPct, entry.getValue());

            // Výpis do dvoch stĺpcov pre lepšiu čitateľnosť
            count++;
            if (count % 2 == 0) {
                System.out.println();
            } else {
                System.out.print("    |  ");
            }
        }
        if (count % 2 != 0) System.out.println();

        System.out.println("==================================================");
    }

    private static void printTopWords(Map<String, Integer> map, int limit) {
        List<Map.Entry<String, Integer>> sorted = map.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            System.out.println("  Žiadne výskyty.");
            return;
        }

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            System.out.printf("  %2d. %-15s (%d x)\n", i + 1, entry.getKey(), entry.getValue());
        }
    }

    // Zoberie cestu k adresáru a načíta všetky .json súbory
    public static Map<String, Layout> loadLayoutsFromDirectory(String dirPath) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        Path dir = Path.of(dirPath);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("UPOZORNENIE: Adresár s layoutmi '" + dir.toAbsolutePath() + "' neexistuje.");
            return layouts;
        }

        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.dof")) {
            for (Path entry : stream) {
//                System.out.print("  Čítam súbor " + entry.getFileName());
                String content = Files.readString(entry);
                Layout layout = parseJsonToLayout(content);
                if (layout != null) {
                    layouts.put(layout.name, layout);
//                    System.out.println("  ✓ Načítaný layout: " + layout.name + " zo súboru " + entry.getFileName());
                } else {
                    System.err.println("  ❌ Chyba parsovania v súbore: " + entry.getFileName());
                }
            }
        } catch (IOException e) {
            System.err.println("Chyba pri čítaní adresára: " + e.getMessage());
        }
        return layouts;
    }

    // --- NOVÝ TOKENS-SAFE OBJEKTOVÝ PARSER S JACKSONOM ---
    private static Layout parseJsonToLayout(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            String name = root.has("name") ? root.get("name").asText() : "Unknown";

            if (root.has("layers") && root.get("layers").has("main")) {
                JsonNode mainBlock = root.get("layers").get("main");

                if (mainBlock.size() >= 3) {
                    String[] cleanRows = new String[3];
                    for (int i = 0; i < 3; i++) {
                        // Čistá extrakcia stringu bez ohľadu na prítomnosť špeciálnych znakov
                        String rawRow = mainBlock.get(i).asText();
                        cleanRows[i] = rawRow.replaceAll("\\s+", "");
                        System.out.println("Čistý riadok: '" + cleanRows[i] + "'");
                    }
                    return new Layout(name, cleanRows);
                } else {
                    System.err.println("  ❌ Malo riadko v layoute");
                }
            } else {
                System.err.println("  ❌ Prazdny layout");
            }
        } catch (Exception e) {
            System.err.println("  ❌ Chyba Jackson objektového parsovania: " + e.getMessage());
        }
        return null;
    }

    // Bez-knižnicový regex parser pre špecifický formát klávesníc
    private static Layout parseJsonToLayout2(String json) {
        String name = "Unknown";

        // 1. Vytiahnutie mena layoutu
        java.util.regex.Matcher nameMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (nameMatcher.find()) {
            name = nameMatcher.group(1);
        }

        // 2. Vytiahnutie bloku "main": [ ... ]
        java.util.regex.Matcher mainMatcher = Pattern.compile("\"main\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (mainMatcher.find()) {
            String mainBlock = mainMatcher.group(1);

            // 3. Vytiahnutie jednotlivých riadkov s úvodzovkami
            java.util.regex.Matcher rowMatcher = Pattern.compile("\"([^\"]+)\"").matcher(mainBlock);
            List<String> rawRows = new ArrayList<>();
            while (rowMatcher.find()) {
                rawRows.add(rowMatcher.group(1));
            }

            // 4. Konverzia na čisté 10-znakové stringy (odstránenie bielych znakov)
            if (rawRows.size() >= 3) {
                String[] cleanRows = new String[3];
                for (int i = 0; i < 3; i++) {
                    // Prevedie "q  g  d  f..." na čisté "qgdf..."
                    cleanRows[i] = rawRows.get(i).replaceAll("\\s+", "");
                }
                return new Layout(name, cleanRows);
            }
        }
        return null;
    }

    // --- NOVÁ METÓDA PRE PREHĽADNÝ SUMÁRNY LEADERBOARD ---
    public static void printLeaderboard(List<AnalysisResult> results, String sortedByCriteria) {
        System.out.println("\n====================================================================================================");
        System.out.println("   VÝSLEDNÁ LISTINA LAYOUTOV (Zoradené podľa: " + sortedByCriteria.toUpperCase() + ")");
        System.out.println("====================================================================================================");
        System.out.printf("%-5s | %-22s | %-9s | %-7s | %-7s | %-10s | %-12s | %-10s\n",
                "Idx", "Názov layoutu", "BAL_SCORE", "SFB %", "SFS %", "InnerStr %", "Alternácia %", "Jedn. behy");
        System.out.println("----------------------------------------------------------------------------------------------------");

        int i = 0;
        for (AnalysisResult res : results) {
            System.out.printf("%-5d | %-22s | %8.2f  | %5.2f %% | %5.2f %% | %8.2f %% | %10.2f %% | %dx\n",
                    ++i,
                    res.getLayoutName(),
                    res.getBalancedScore(),
                    res.getSfbPct(),
                    res.getSfsPct(),
                    res.getInnerStretchPct(),
                    res.getAlternationPct(),
                    res.getLongOneHandedRuns()
            );
        }
        System.out.println("====================================================================================================");
    }

    // --- NOVÁ METÓDA PRE MATEMATICKÚ DYNAMICKÚ VÁHUTU (GEOMETRICKÝ PRIEMER) ---
    public static void calculateWeightedBalancedScores(List<AnalysisResult> results, Map<String, Double> weights) {
        if (results.isEmpty()) return;

        double minSfb = Double.MAX_VALUE, maxSfb = Double.MIN_VALUE;
        double minSfs = Double.MAX_VALUE, maxSfs = Double.MIN_VALUE;
        double minStretch = Double.MAX_VALUE, maxStretch = Double.MIN_VALUE;
        double minAlt = Double.MAX_VALUE, maxAlt = Double.MIN_VALUE;
        double minRuns = Double.MAX_VALUE, maxRuns = Double.MIN_VALUE;

        // Vytiahneme váhy z mapy
        double wSfb = weights.getOrDefault("SFB", 1.0);
        double wSfs = weights.getOrDefault("SFS", 1.0);
        double wStretch = weights.getOrDefault("STRETCH", 1.0);
        double wAlt = weights.getOrDefault("ALT", 1.0);
        double wRuns = weights.getOrDefault("RUNS", 1.0);
        double totalWeight = wSfb + wSfs + wStretch + wAlt + wRuns;

        // 1. Krok: Analýza rozsahu dát celého poolu
        for (AnalysisResult r : results) {
            minSfb = Math.min(minSfb, r.getSfbPct());
            maxSfb = Math.max(maxSfb, r.getSfbPct());
            minSfs = Math.min(minSfs, r.getSfsPct());
            maxSfs = Math.max(maxSfs, r.getSfsPct());
            minStretch = Math.min(minStretch, r.getInnerStretchPct());
            maxStretch = Math.max(maxStretch, r.getInnerStretchPct());
            minAlt = Math.min(minAlt, r.getAlternationPct());
            maxAlt = Math.max(maxAlt, r.getAlternationPct());
            minRuns = Math.min(minRuns, r.getLongOneHandedRuns());
            maxRuns = Math.max(maxRuns, r.getLongOneHandedRuns());
        }

        // 2. Krok: Škálovanie a aplikácia váženého geometrického priemeru
        for (AnalysisResult r : results) {
            double sfbScore = maxSfb == minSfb ? 1.0 : 0.01 + 0.99 * ((maxSfb - r.getSfbPct()) / (maxSfb - minSfb));
            double sfsScore = maxSfs == minSfs ? 1.0 : 0.01 + 0.99 * ((maxSfs - r.getSfsPct()) / (maxSfs - minSfs));
            double stretchScore = maxStretch == minStretch ? 1.0 : 0.01 + 0.99 * ((maxStretch - r.getInnerStretchPct()) / (maxStretch - minStretch));
            double altScore = maxAlt == minAlt ? 1.0 : 0.01 + 0.99 * ((r.getAlternationPct() - minAlt) / (maxAlt - minAlt));
            double runsScore = maxRuns == minRuns ? 1.0 : 0.01 + 0.99 * ((maxRuns - r.getLongOneHandedRuns()) / (maxRuns - minRuns));

            // Vážený geometrický priemer: váha funguje ako exponent (mocnina)
            double poweredProduct = Math.pow(sfbScore, wSfb) * Math.pow(sfsScore, wSfs) * Math.pow(stretchScore, wStretch) * Math.pow(altScore, wAlt) * Math.pow(runsScore, wRuns);

            r.balancedScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0;
        }
    }

    public static void main(String[] args) {
        // Cesty môžeme prijímať z argumentov, inak použijeme predvolené
        String korpusPath = "korpus.txt";
        String layoutsDir = "layouts"; // Tvoj adresár s .json súbormi

        if (args.length >= 1) korpusPath = args[0];
        if (args.length >= 2) layoutsDir = args[1];

        System.out.println("Aktuálny pracovný adresár: " + System.getProperty("user.dir"));

        // 1. Načítanie všetkých layoutov zo zložky
        System.out.println("\nHľadám .json layouty v adresári: " + Path.of(layoutsDir).toAbsolutePath());
        Map<String, Layout> layoutsToTest = loadLayoutsFromDirectory(layoutsDir);

        if (layoutsToTest.isEmpty()) {
            System.err.println("Nenašli sa žiadne platné layouty na testovanie. Končím.");
            return;
        }

        // 2. Načítanie a spracovanie korpusu
        try {
            Path cestaKSuboru = Path.of(korpusPath).toAbsolutePath();
            System.out.println("\nNačítavam korpus z: " + cestaKSuboru);
            String rawKorpus = Files.readString(cestaKSuboru);

            if (rawKorpus.strip().isEmpty()) {
                System.err.println("Chyba: Načítaný súbor s korpusom je prázdny!");
                return;
            }

            System.out.println("\nSpúšťam masívnu analýzu pre " + layoutsToTest.size() + " layoutov...\n");

            // List na ukladanie nespracovaných DTO objektov pred ich finálnym zoradením
            List<AnalysisResult> allResults = new ArrayList<>();

            // 3. Spustenie analýzy pre každý načítanud layout
            for (Map.Entry<String, Layout> entry : layoutsToTest.entrySet()) {
                Stats stats = analyze(rawKorpus, entry.getValue());
                allResults.add(new AnalysisResult(entry.getKey(), stats));
            }

            // =========================================================================
            //  KONFIGURÁCIA VÁH PRE EXPERIMENTY (Uprav si čísla podľa potreby)
            // =========================================================================
            Map<String, Double> weights = new HashMap<>();
//            Profil: Vahy pre elitu
            weights.put("SFB", 1.0);      // Lineárna váha. Elita to má pod 2.5%, netreba ich umelo popravovať za stotiny.
            weights.put("SFS", 0.0);      // Lineárna váha. Preskakovanie riadkov je nepríjemné, ale 1.0 stačí.
            weights.put("STRETCH", 2.0);  // Dvojnásobný trest. Na Toteme chceme stredový stĺpec ignorovať čo najviac.
            weights.put("ALT", 2.0);      // Dvojnásobný bonus! Tvoja odpoveď na slepú uličku. Odmeňujeme klávesnice, ktoré hrajú ping-pong.
            weights.put("RUNS", 2.0);     // Dvojnásobný trest. Ak layout nedokáže alternovať a nechá ťa v kŕči na jednej ruke, ide z hry von.//            Profil: Klavírny Flow (Maximálny bubnovací rytmus)
//            weights.put("SFB", 10.0);      // Váha pre kmitanie rovnakého prsta pod sebou
//            weights.put("SFS", 6.0);      // Váha pre kmitanie rovnakého prsta cez jeden znak (Skipgrams)
//            weights.put("STRETCH", 4.0);  // Váha pre penalizáciu naťahovania ukazovákov do strán
//            weights.put("ALT", 8.0);      // Váha pre bonus za plynulé striedanie rúk (Rhythm)
//            weights.put("RUNS", 3.0);     // Váha pre penalizáciu kŕčovitého držania slova na jednej ruke

            // Výpočet váženého skóre a zoradenie (Zostupne - najlepší všestranný stred je prvý)
            calculateWeightedBalancedScores(allResults, weights);
            allResults.sort(Comparator.comparingDouble(AnalysisResult::getBalancedScore).reversed());


            // Výpis detailných reportov v novo zotriedenom poradí
            for (AnalysisResult res : allResults) {
                printReport(res.stats, res.layoutName);
            }

            // Tlač záverečného sumárneho rebríčka pre rýchle globálne porovnanie
            printLeaderboard(allResults, "Užívateľské vážené skóre");

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní korpusu '" + korpusPath + "'. Detaily: " + e.getMessage());
        }
    }
}
