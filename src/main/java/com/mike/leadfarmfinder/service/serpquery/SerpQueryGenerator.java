package com.mike.leadfarmfinder.service.serpquery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class SerpQueryGenerator {

    static final List<String> PRODUCTS = List.of(
            "Spargel", "Erdbeeren", "Kartoffeln", "Zwiebeln", "Möhren",
            "Gurken", "Tomaten", "Salat", "Kürbis", "Zucchini",
            "Blumenkohl", "Brokkoli", "Kohlrabi", "Rosenkohl", "Grünkohl",
            "Paprika", "Sellerie", "Lauch", "Porree", "Spitzkohl",
            "Weißkohl", "Rotkohl", "Rote Bete", "Radieschen",
            "Äpfel", "Kirschen", "Himbeeren", "Heidelbeeren",
            "Zwetschgen", "Birnen", "Pflaumen", "Stachelbeeren",
            "Johannisbeeren", "Brombeeren", "Weintrauben", "Rhabarber",
            "Aronia", "Sanddorn", "Holunder",
            "Dinkel", "Emmer", "Roggen", "Hafer", "Weizen",
            "Sonnenblumen", "Raps", "Kürbiskerne", "Leinsamen", "Mohn",
            "Petersilie", "Schnittlauch", "Dill", "Basilikum", "Koriander",
            "Zitronenmelisse", "Salbei", "Oregano", "Minze", "Bärlauch",
            "Pastinaken", "Schwarzwurzel", "Meerrettich", "Steckrüben",
            "Mangold", "Rucola", "Artischocken", "Zuckermais",
            "Shiitake", "Austernpilze", "Kräuterseitlinge",
            "Honig", "Eier", "Milch", "Käse", "Fleisch", "Wurst",
            "Apfelsaft", "Apfelmost", "Marmelade"
    );

    static final List<String> REGIONS = List.of(
            // Bayern — silnie rolnicze Landkreise
            "Landsberg am Lech", "Ebersberg", "Dachau", "Freising",
            "Erding", "Rosenheim", "Traunstein", "Altötting", "Mühldorf am Inn",
            "Passau", "Deggendorf", "Straubing-Bogen", "Regensburg",
            "Neumarkt", "Amberg-Sulzbach", "Weiden", "Bayreuth",
            "Coburg", "Bamberg", "Ansbach", "Weißenburg-Gunzenhausen",
            "Ingolstadt", "Pfaffenhofen", "Aichach-Friedberg",
            "Dillingen", "Günzburg", "Neu-Ulm",
            "Oberallgäu", "Ostallgäu", "Chiemgau",
            "Allgäu", "Franken", "Oberbayern", "Oberpfalz",
            // Bayern — nowe rolnicze
            "Dingolfing-Landau", "Rottal-Inn", "Kelheim", "Regen",
            "Cham", "Schwandorf", "Tirschenreuth",

            // Niedersachsen — bardzo rolnicze
            "Lüneburg", "Uelzen", "Celle", "Verden", "Rotenburg Wümme",
            "Cuxhaven", "Stade", "Harburg", "Heidekreis", "Soltau",
            "Hameln-Pyrmont", "Northeim", "Göttingen", "Wolfenbüttel",
            "Osnabrück", "Emsland", "Cloppenburg", "Vechta",
            "Nienburg", "Schaumburg",
            "Lüneburger Heide",
            // Niedersachsen — nowe
            "Wesermarsch", "Friesland", "Wittmund", "Aurich",
            "Oldenburg Land", "Diepholz", "Gifhorn",

            // NRW — rolnicze (bez przemysłowych)
            "Viersen", "Neuss", "Münsterland",
            "Borken", "Coesfeld", "Steinfurt", "Warendorf",
            "Soest", "Paderborn", "Gütersloh", "Minden-Lübbecke",
            "Kleve", "Wesel",

            // Brandenburg — bardzo rolnicze
            "Oder-Spree", "Havelland", "Prignitz", "Uckermark",
            "Dahme-Spreewald", "Elbe-Elster", "Fläming",
            "Märkisch-Oderland", "Spreewald",
            "Ostprignitz-Ruppin", "Oberspreewald-Lausitz",

            // Sachsen — rolnicze
            "Nordsachsen", "Meißen", "Bautzen", "Görlitz",
            "Mittelsachsen", "Leipzig Land",

            // Thüringen — rolnicze
            "Nordhausen", "Kyffhäuserkreis", "Eichsfeld",
            "Unstrut-Hainich", "Wartburgkreis", "Hildburghausen",
            "Altenburger Land",

            // Sachsen-Anhalt — bardzo rolnicze, duże gospodarstwa
            "Börde", "Jerichower Land", "Altmarkkreis Salzwedel",
            "Anhalt-Bitterfeld", "Wittenberg", "Mansfeld-Südharz",

            // Hessen — rolnicze
            "Wetteraukreis", "Rheingau-Taunus", "Main-Kinzig",
            "Vogelsbergkreis", "Marburg-Biedenkopf",

            // Baden-Württemberg — rolnicze i winiarskie
            "Kraichgau", "Hohenlohe", "Schwäbische Alb", "Oberschwaben",
            "Hegau", "Markgräflerland", "Kaiserstuhl", "Breisgau",
            "Ortenau", "Bodenseekreis",
            "Rhein-Neckar-Kreis", "Heilbronn Land", "Ludwigsburg Land",

            // Rheinland-Pfalz — rolnicze i winiarskie
            "Pfalz", "Eifel", "Hunsrück", "Mosel",
            "Rhein-Hunsrück", "Bad Kreuznach", "Alzey-Worms",

            // Mecklenburg-Vorpommern — duże gospodarstwa zbożowe
            "Mecklenburg", "Vorpommern-Rügen", "Vorpommern-Greifswald",
            "Mecklenburgische Seenplatte", "Rostock Land",

            // Schleswig-Holstein — rolnicze
            "Schleswig", "Pinneberg",
            "Dithmarschen", "Steinburg", "Rendsburg-Eckernförde",
            "Schleswig-Flensburg", "Nordfriesland",

            // Bodensee
            "Bodensee"
    );

    static final List<String> IMPRESSUM_FILTERS = List.of(
            "\"Verantwortlich für den Inhalt\"",
            "\"Inhaber\"",
            "\"Betreiber\"",
            "\"GbR\"",
            "\"Steuernummer\"",
            "\"USt-IdNr\"",
            "\"Einzelunternehmen\"",
            "\"Landwirt\"",
            "\"Landwirtin\"",
            "\"e.K.\""
    );

    static final List<String> FIRST_PERSON_FILTERS = List.of(
            "\"auf unserem Hof\"",
            "\"wir bauen an\"",
            "\"aus eigenem Anbau\"",
            "\"vom eigenen Hof\"",
            "\"direkt vom Hof\"",
            "\"ab Hof\"",
            "\"frisch vom Hof\"",
            "\"selbst angebaut\"",
            "\"eigener Anbau\"",
            "\"hofeigene\""
    );

    static final List<String> OPERATIONAL_FILTERS = List.of(
            "\"Abholtermin\"",
            "\"Vorbestellung\"",
            "\"Liefertag\"",
            "\"Öffnungszeiten\"",
            "\"Direktverkauf\""
    );

    static final List<String> INTENTS = List.of(
            "Kontakt", "Adresse", "Telefon"
    );

    static final List<String> FARM_TYPES = List.of(
            "Hof", "Hofladen", "Bauernhof", "Betrieb", "Gärtnerei",
            "Obstbetrieb", "Gemüsehof", "Obsthof", "Biohof"
    );

    public String generate(int cycleIndex, Set<String> existingQueries) {
        String product = PRODUCTS.get(cycleIndex % PRODUCTS.size());
        String region = REGIONS.get((cycleIndex * 3 + 7) % REGIONS.size());
        String farmType = FARM_TYPES.get((cycleIndex * 2 + 1) % FARM_TYPES.size());
        String intent = INTENTS.get(cycleIndex % INTENTS.size());

        int filterType = (cycleIndex / PRODUCTS.size()) % 3;

        String query;
        if (filterType == 0) {
            String filter = IMPRESSUM_FILTERS.get((cycleIndex / PRODUCTS.size()) % IMPRESSUM_FILTERS.size());
            query = String.format("%s %s %s %s", filter, product, region, intent);
        } else if (filterType == 1) {
            String filter = FIRST_PERSON_FILTERS.get((cycleIndex / PRODUCTS.size()) % FIRST_PERSON_FILTERS.size());
            query = String.format("%s %s %s %s", filter, product, region, intent);
        } else {
            String filter = OPERATIONAL_FILTERS.get((cycleIndex / PRODUCTS.size()) % OPERATIONAL_FILTERS.size());
            query = String.format("%s %s %s %s %s", filter, farmType, product, region, intent);
        }

        if (existingQueries.contains(query)) {
            int fallbackRegionIdx = (cycleIndex * 7 + 13) % REGIONS.size();
            String fallbackRegion = REGIONS.get(fallbackRegionIdx);
            query = query.replace(region, fallbackRegion);
            log.debug("SerpQueryGenerator: collision, fallback region='{}' for index={}", fallbackRegion, cycleIndex);
        }

        log.info("SerpQueryGenerator: generated query='{}' (index={}, product='{}', region='{}')",
                query, cycleIndex, product, region);
        return query;
    }

    public List<String> generateBatch(int count, Set<String> existingQueries) {
        List<String> result = new ArrayList<>();
        Set<String> generated = new HashSet<>(existingQueries);

        for (int i = 0; result.size() < count; i++) {
            String query = generate( i, generated);
            if (!generated.contains(query)) {
                result.add(query);
                generated.add(query);
            }
            if (i > count * 10) {
                log.warn("SerpQueryGenerator: could not generate {} unique queries after {} attempts", count, i);
                break;
            }
        }
        return Collections.unmodifiableList(result);
    }
}