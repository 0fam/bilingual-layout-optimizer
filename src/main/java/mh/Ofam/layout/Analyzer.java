package mh.Ofam.layout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mh.Ofam.helper.FileSystemHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class Analyzer {

    // Zoberie cestu k adresáru a načíta všetky .json súbory
    public static Map<String, Layout> loadLayoutsFromDirectory(Path dir) {
        Map<String, Layout> layouts = new LinkedHashMap<>();

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
        // Nové metriky
        double minScissors = Double.MAX_VALUE, maxScissors = Double.MIN_VALUE;
        double minRedirects = Double.MAX_VALUE, maxRedirects = Double.MIN_VALUE;
        double minRollIn = Double.MAX_VALUE, maxRollIn = Double.MIN_VALUE;
        double minRollOut = Double.MAX_VALUE, maxRollOut = Double.MIN_VALUE;

        // Vytiahneme váhy z mapy
        double wSfb = weights.getOrDefault("SFB", 1.0);
        double wSfs = weights.getOrDefault("SFS", 1.0);
        double wStretch = weights.getOrDefault("STRETCH", 1.0);
        double wAlt = weights.getOrDefault("ALT", 1.0);
        double wRuns = weights.getOrDefault("RUNS", 1.0);
        double wScissors = weights.getOrDefault("SCISSORS", 1.0);
        double wRedirects = weights.getOrDefault("REDIRECTS", 1.0);
        double wRollIn = weights.getOrDefault("ROLL_IN", 1.0);
        double wRollOut = weights.getOrDefault("ROLL_OUT", 1.0);

        double totalWeight = wSfb + wSfs + wStretch + wAlt + wRuns + wScissors + wRedirects + wRollIn + wRollOut;

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
            minScissors = Math.min(minScissors, r.getScissorsPct());
            maxScissors = Math.max(maxScissors, r.getScissorsPct());
            minRedirects = Math.min(minRedirects, r.getRedirectsPct());
            maxRedirects = Math.max(maxRedirects, r.getRedirectsPct());
            minRollIn = Math.min(minRollIn, r.getRollInPct());
            maxRollIn = Math.max(maxRollIn, r.getRollInPct());
            minRollOut = Math.min(minRollOut, r.getRollOutPct());
            maxRollOut = Math.max(maxRollOut, r.getRollOutPct());

        }

        // 2. Krok: Škálovanie a aplikácia váženého geometrického priemeru
        for (AnalysisResult r : results) {
            // Tresty (menšie je lepšie)
            double sfbScore = maxSfb == minSfb ? 1.0 : 0.01 + 0.99 * ((maxSfb - r.getSfbPct()) / (maxSfb - minSfb));
            double sfsScore = maxSfs == minSfs ? 1.0 : 0.01 + 0.99 * ((maxSfs - r.getSfsPct()) / (maxSfs - minSfs));
            double stretchScore = maxStretch == minStretch ? 1.0 : 0.01 + 0.99 * ((maxStretch - r.getInnerStretchPct()) / (maxStretch - minStretch));
            double runsScore = maxRuns == minRuns ? 1.0 : 0.01 + 0.99 * ((maxRuns - r.getLongOneHandedRuns()) / (maxRuns - minRuns));
            double scissorsScore = maxScissors == minScissors ? 1.0 : 0.01 + 0.99 * ((maxScissors - r.getScissorsPct()) / (maxScissors - minScissors));
            double redirectsScore = maxRedirects == minRedirects ? 1.0 : 0.01 + 0.99 * ((maxRedirects - r.getRedirectsPct()) / (maxRedirects - minRedirects));

            // Odmeny (väčšie je lepšie -> otočená logika ako pri ALTs)
            double altScore = maxAlt == minAlt ? 1.0 : 0.01 + 0.99 * ((r.getAlternationPct() - minAlt) / (maxAlt - minAlt));
            double rollInScore = maxRollIn == minRollIn ? 1.0 : 0.01 + 0.99 * ((r.getRollInPct() - minRollIn) / (maxRollIn - minRollIn));
            double rollOutScore = maxRollOut == minRollOut ? 1.0 : 0.01 + 0.99 * ((r.getRollOutPct() - minRollOut) / (maxRollOut - minRollOut));

            // Vážený geometrický priemer: váha funguje ako exponent (mocnina)
            double poweredProduct =
                    Math.pow(sfbScore, wSfb) * Math.pow(sfsScore, wSfs) * Math.pow(stretchScore, wStretch) * Math.pow(altScore, wAlt) * Math.pow(runsScore, wRuns) *
                            Math.pow(scissorsScore, wScissors) *
                            Math.pow(redirectsScore, wRedirects) *
                            Math.pow(rollInScore, wRollIn) *
                            Math.pow(rollOutScore, wRollOut);

            r.balancedScore = Math.pow(poweredProduct, 1.0 / totalWeight) * 100.0;
        }
    }

    public static void main(String[] args) throws URISyntaxException {
        // Cesty môžeme prijímať z argumentov, inak použijeme predvolené
        String korpusPath = "korpus.txt";
        String layoutsDir = "layouts"; // Tvoj adresár s .json súbormi

        if (args.length >= 1) korpusPath = args[0];
        if (args.length >= 2) layoutsDir = args[1];

        // 1. Načítanie všetkých layoutov zo zložky
        Path laoutsDirPath = FileSystemHelper.getResourcePathForDir(layoutsDir);
        log.info("\nHľadám .json layouty v adresári: {}", laoutsDirPath.toAbsolutePath());
        Map<String, Layout> layoutsToTest = loadLayoutsFromDirectory(laoutsDirPath);

        if (layoutsToTest.isEmpty()) {
            System.err.println("Nenašli sa žiadne platné layouty na testovanie. Končím.");
            return;
        }

        // 2. Načítanie a spracovanie korpusu
        try {
            Path cestaKSuboru = FileSystemHelper.getResourcePathForDir(korpusPath);
            System.out.println("\nNačítavam korpus z: " + cestaKSuboru.toAbsolutePath());
            String rawKorpus = Files.readString(cestaKSuboru);

            if (rawKorpus.strip().isEmpty()) {
                System.err.println("Chyba: Načítaný súbor s korpusom je prázdny!");
                return;
            }
            TextStats textStats = TextStats.instance(rawKorpus);

            System.out.println("\nSpúšťam masívnu analýzu pre " + layoutsToTest.size() + " layoutov...\n");

            // List na ukladanie nespracovaných DTO objektov pred ich finálnym zoradením
            List<AnalysisResult> allResults = new ArrayList<>();

            // 3. Spustenie analýzy pre každý načítanud layout
            for (Map.Entry<String, Layout> entry : layoutsToTest.entrySet()) {
                LayoutStats layoutStats = LayoutStats.instance(textStats, entry.getValue());
                allResults.add(new AnalysisResult(entry.getKey(), layoutStats));
            }

            // =========================================================================
            //  KONFIGURÁCIA VÁH PRE EXPERIMENTY (Uprav si čísla podľa potreby)
            // =========================================================================
            Map<String, Double> weights = new HashMap<>();

// --- TVOJA SVÄTÁ TROJICA (Absolútna priorita) ---
            weights.put("STRETCH", 5.0);    // Extrémny trest za naťahovanie ukazovákov. Na Toteme chceme ruky v základnej pozícii.
            weights.put("REDIRECTS", 5.0);  // Extrémny trest za "Pinball" (Zákaz lámania smeru na jednej ruke).
            weights.put("SFS", 3.0);        // Tvrdý trest za Skipgramy (preskakovanie jedného znaku tým istým prstom).

// --- DRŽÍME NA UZDE (Aby sa algoritmus nezbláznil) ---
            weights.put("SFB", 1.0);        // Znižujeme na minimum. Len aby nám sem nepreliezol nejaký odpadový layout s 8 % SFB. Pre elitu to prestane hrať rolu.

// --- IGNORUJEME (Štatistický šum a nepotrebné metriky) ---
            weights.put("SCISSORS", 0.0);   // Presne ako hovoríš. Pod 1 % to nemá zmysel riešiť. Vypíname.
            weights.put("ROLL_IN", 0.0);    // Vypíname, spoľahneme sa na to, že nízke Redirects automaticky zabezpečia plynulosť.
            weights.put("ROLL_OUT", 0.0);
            weights.put("ALT", 0.0);
            weights.put("RUNS", 0.0);

// --- ZABIJACI (Hardcore tresty) ---
//            weights.put("SFB", 5.0);        // Absolútny kráľ bolesti. Kmitanie jedným prstom netolerujeme.
//            weights.put("SCISSORS", 3.0);   // Kríženie prstov medzi riadkami je ergonomické peklo.
//            weights.put("REDIRECTS", 3.0);  // Zvýšená váha z 2.0 na 3.0! Keďže už neriešime "pekné" rolly, musíme o to tvrdšie trestať "pinball" (sekanie smeru), inak nám ruka na jednej strane umrie.
//            weights.put("STRETCH", 2.0);    // Strážca Totemu. Ukazováky zostanú doma.
//            weights.put("SFS", 1.0);        // Skipgramy nechávame ako jemný filter proti sendvičovému kmitaniu.
//
//// --- VYPNUTÝ ŠUM (Všetko na nulu) ---
//            weights.put("ROLL_IN", 0.0);
//            weights.put("ROLL_OUT", 0.0);
//            weights.put("ALT", 0.0);
//            weights.put("RUNS", 0.0);

            // --- PROFIL: PURE FLOW & TENDON HEALTH ---
//            weights.put("SFB", 5.0);
//            weights.put("SCISSORS", 3.0);
//            weights.put("STRETCH", 2.0);
//            weights.put("REDIRECTS", 2.0);
//            weights.put("SFS", 1.0);
//
//            // Odmeny musia mať kladné váhy, pretože logika odmeňovania je už preklopená pri výpočte skóre
//            weights.put("ROLL_IN", 1.0);
//            weights.put("ROLL_OUT", 0.5);
//
//            // Ignorujeme alternáciu a valivé behy trestáme iba jemne
//            weights.put("ALT", 0.0);
//            weights.put("RUNS", 0.5);

            // Výpočet váženého skóre a zoradenie (Zostupne - najlepší všestranný stred je prvý)
            calculateWeightedBalancedScores(allResults, weights);
            allResults.sort(Comparator.comparingDouble(AnalysisResult::getBalancedScore).reversed());

            // Tlač záverečného sumárneho rebríčka pre rýchle globálne porovnanie
            printLeaderboard(allResults, "Užívateľské vážené skóre");

        } catch (IOException e) {
            System.err.println("Chyba pri čítaní korpusu '" + korpusPath + "'. Detaily: " + e.getMessage());
        }
    }
}
