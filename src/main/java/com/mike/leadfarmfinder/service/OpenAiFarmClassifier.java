package com.mike.leadfarmfinder.service.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.dto.FarmClassificationResult;
import com.mike.leadfarmfinder.service.OpenAiService;
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
                You are a classifier for a seasonal farm worker lead generation system.
                The user will give you:
                1) URL of a website
                2) Extracted plain text of the page (truncated).
                
                Your task:
                - Decide if this website is a small or medium German farm.
                - Decide if this farm offers seasonal agricultural jobs (fruits, vegetables,
                  asparagus, berries, apples, Christmas trees, etc.).
                
                IMPORTANT RULES:
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
                
                - Only consider as "farm" if it clearly represents a specific agricultural
                  business (Hof, Landhof, Obsthof, Spargelhof, Erdbeerhof, Bauernhof etc.).
                - "is_seasonal_jobs" should be true only if the text clearly mentions
                  seasonal work, harvest workers, Saisonarbeit, Erntehelfer, Studentenjobs
                  on farm etc.
                
                URL:
                %s
                
                TEXT:
                %s
                
                Respond ONLY in valid JSON with fields:
                {
                  "is_farm": boolean,
                  "is_seasonal_jobs": boolean,
                  "reason": string,
                  "main_contact_url": string | null
                }
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
