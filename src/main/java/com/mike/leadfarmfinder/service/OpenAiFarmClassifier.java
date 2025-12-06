package com.mike.leadfarmfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiFarmClassifier {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    public FarmClassificationResult classifyFarm(String url, String textSnippet) {
        String prompt = """
                You are a STRICT classifier for a seasonal farm worker lead generation system.
                
                INPUT:
                - url: the URL of a website
                - page_text: extracted plain text of the page (possibly truncated)
                
                YOUR TASK:
                1) Decide if this website represents ONE specific small or medium German FARM business.
                2) Decide if this farm offers SEASONAL AGRICULTURAL JOBS
                   (fruits, vegetables, asparagus, berries, apples, vineyards, Christmas trees, etc.).
                
                STRICT NEGATIVE RULES (VERY IMPORTANT):
                - Regional newspapers, news portals, city magazines, lifestyle portals, blogs, tourist organizations,
                  and campsites are NOT farms, even if they contain job advertisements for farm work.
                  Examples of NOT-farms:
                  * "Kielerleben" (city/region lifestyle portal)
                  * "Sauerlandkurier" (regional newspaper / portal)
                  * any "Stadtmagazin", "Stadtportal", "Tourismusverband", "Reiseland", etc.
                  In ALL these cases you MUST answer: "is_farm": false.
                
                - If the website lists MANY different farms, attractions, companies, events or tourist offers in a region
                  (directories, portals, tourism pages), it is NOT itself a farm business.
                
                - If the URL domain is a large job portal, social network or generic site
                  (for example: indeed.com, stepstone.de, meinestadt.de, facebook.com,
                  instagram.com, linkedin.com, youtube.com, tiktok.com, xing.com, etc.),
                  or any obvious non-farm platform, ALWAYS answer:
                  {
                    "is_farm": false,
                    "is_seasonal_jobs": false,
                    "reason": "job-portal-or-social-network",
                    "main_contact_url": null
                  }
                
                POSITIVE RULES (WHEN TO RETURN is_farm = true):
                - Only consider "is_farm": true if the website clearly represents ONE specific agricultural business:
                  e.g. Hof, Landhof, Obsthof, Spargelhof, Erdbeerhof, Bauernhof, Biohof, Weingut, Winzer, etc.
                - Typical signs:
                  * there is one main farm name (e.g. "Erdbeerhof Müller", "Obsthof Schmidt"),
                  * there is an address and contact data for THIS farm,
                  * the content describes their own products, fields, orchards, animals, etc.
                
                SEASONAL JOBS:
                - "is_seasonal_jobs" should be true ONLY if the text clearly mentions seasonal work on the farm, for example:
                  "Saisonarbeit", "Erntehelfer", "Erntehilfe", "Erntehelfer:innen",
                  "Erntejobs", "Saisonkräfte", "Ferienjob auf dem Hof",
                  "Studentenjobs auf unserem Hof" or similar.
                - If seasonal work is not clearly mentioned, use "is_seasonal_jobs": false.
                
                DECISION POLICY:
                - If you are NOT clearly sure that this is ONE specific farm business, you MUST answer "is_farm": false.
                - Portals, media, city / regional magazines, tourism pages and directories are always "is_farm": false.
                - Do NOT guess "is_farm": true only because the text talks about farms in general or many different farms.
                
                OUTPUT FORMAT:
                Respond ONLY with a single valid JSON object:
                {
                  "is_farm": boolean,
                  "is_seasonal_jobs": boolean,
                  "reason": string,
                  "main_contact_url": string | null
                }
                
                "reason" should be a short explanation like:
                - "single-farm-website-with-products-and-contact"
                - "regional-media-portal-not-a-farm"
                - "tourism-portal-listing-many-farms"
                - "job-portal-or-social-network"
                - "no-clear-sign-of-farm"
                
                INPUT DATA:
                URL:
                %s
                
                PAGE_TEXT (first 2000 chars approx.):
                %s
                """.formatted(url, textSnippet);

        String json = openAiService.classify(prompt);
        if (json == null || json.isBlank()) {
            log.warn("OpenAiFarmClassifier: empty JSON from OpenAI, treating as not-a-farm");
            return new FarmClassificationResult(false, false, "empty-openai-response", null);
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            boolean isFarm = node.path("is_farm").asBoolean(false);
            boolean isSeasonalJobs = node.path("is_seasonal_jobs").asBoolean(false);
            String reason = node.path("reason").asText("no-reason");
            JsonNode contactNode = node.path("main_contact_url");

            String mainContactUrl = contactNode.isMissingNode() || contactNode.isNull()
                    ? null
                    : contactNode.asText(null);

            FarmClassificationResult result = new FarmClassificationResult(
                    isFarm,
                    isSeasonalJobs,
                    reason,
                    mainContactUrl
            );

            log.info("OpenAiFarmClassifier: url={} -> isFarm={}, isSeasonalJobs={}, contactUrl={}, reason={}",
                    url, result.isFarm(), result.isSeasonalJobs(), result.mainContactUrl(), result.reason());

            return result;

        } catch (Exception e) {
            log.warn("OpenAiFarmClassifier: failed to parse JSON from OpenAI. json='{}'", json, e);
            return new FarmClassificationResult(false, false, "parse-error: " + e.getMessage(), null);
        }
    }
}
