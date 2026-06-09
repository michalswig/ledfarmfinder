package com.mike.leadfarmfinder.service.osm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverpassApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final OsmProperties osmProperties;

    /**
     * Pobiera URL-e farm z Overpass API dla całych Niemiec.
     *
     * Używa nwr (nodes, ways, relations) — bez tego tracimy 528 elementów
     * oznaczonych jako way/relation zamiast node.
     *
     * out center — dla way/relation zwraca centroid zamiast pełnej geometrii.
     * Bez tego way nie mają współrzędnych w odpowiedzi (nie potrzebujemy
     * współrzędnych, ale out center jest wymagane żeby way/relation w ogóle
     * pojawiły się w odpowiedzi z tagami).
     *
     * Dwa osobne zapytania złączone unią zamiast regex na klucz —
     * prostsze, pewniejsze, lepiej obsługiwane przez wszystkie wersje API.
     *
     * Zwraca posortowaną listę URL-i dla stabilnego dedup między runami.
     */
    public List<String> fetchFarmWebsites() {
        String query = buildQuery();

        log.info("OverpassApiClient: querying Overpass API bbox={}", osmProperties.getBbox());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(
                    osmProperties.getOverpassUrl(),
                    request,
                    String.class
            );

            if (response == null || response.isBlank()) {
                log.warn("OverpassApiClient: empty response from Overpass API");
                return Collections.emptyList();
            }

            List<String> urls = parseUrls(response);
            Collections.sort(urls);

            log.info("OverpassApiClient: fetched {} farm URLs from OSM", urls.size());
            return urls;

        } catch (Exception e) {
            log.error("OverpassApiClient: failed to fetch from Overpass API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Buduje zapytanie Overpass QL.
     *
     * Unia dwóch zapytań:
     * - nwr[shop=farm][website](bbox)       — farmy z tagiem "website"
     * - nwr[shop=farm]["contact:website"](bbox) — farmy z tagiem "contact:website"
     *
     * Locale.US w String.format — kluczowe dla wartości bbox.
     * Bez tego na systemach z przecinkiem jako separatorem dziesiętnym
     * "47.3" staje się "47,3" i Overpass zwraca błąd parsowania.
     */
    private String buildQuery() {
        double south = parseBboxValue(0);
        double west  = parseBboxValue(1);
        double north = parseBboxValue(2);
        double east  = parseBboxValue(3);

        return String.format(Locale.US,
                "[out:json][timeout:60];" +
                        "(" +
                        "  nwr[shop=farm][website](%f,%f,%f,%f);" +
                        "  nwr[shop=farm][\"contact:website\"](%f,%f,%f,%f);" +
                        ");" +
                        "out center;",
                south, west, north, east,
                south, west, north, east
        );
    }

    private double parseBboxValue(int index) {
        String[] parts = osmProperties.getBbox().split(",");
        return Double.parseDouble(parts[index].trim());
    }

    private List<String> parseUrls(String json) {
        List<String> urls = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode elements = root.path("elements");

            if (!elements.isArray()) {
                log.warn("OverpassApiClient: no 'elements' array in Overpass response");
                return Collections.emptyList();
            }

            int parsed = 0;
            int skipped = 0;

            for (JsonNode element : elements) {
                JsonNode tags = element.path("tags");
                String url = extractWebsite(tags);
                if (url != null) {
                    urls.add(url);
                    parsed++;
                } else {
                    skipped++;
                }
            }

            log.info("OverpassApiClient: parsed={} skipped={} from {} elements",
                    parsed, skipped, elements.size());

        } catch (Exception e) {
            log.error("OverpassApiClient: failed to parse Overpass response: {}", e.getMessage(), e);
        }

        return urls;
    }

    /**
     * Wyciąga URL ze tagów OSM.
     * Priorytet: "website" > "contact:website".
     * Ignoruje null i blank.
     */
    private String extractWebsite(JsonNode tags) {
        String website = textOrNull(tags, "website");
        if (website != null) {
            return normalizeUrl(website);
        }

        String contactWebsite = textOrNull(tags, "contact:website");
        if (contactWebsite != null) {
            return normalizeUrl(contactWebsite);
        }

        return null;
    }

    private String textOrNull(JsonNode tags, String key) {
        JsonNode node = tags.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }

    /**
     * Dodaje https:// jeśli URL nie ma schematu.
     * Analogicznie do HofladenFinderClient.normalizeWebsite().
     */
    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.strip();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }
}