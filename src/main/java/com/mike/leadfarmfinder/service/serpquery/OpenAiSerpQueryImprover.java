package com.mike.leadfarmfinder.service.serpquery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mike.leadfarmfinder.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiSerpQueryImprover implements SerpQueryImproverPort {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;
    private final SerpQueryImproverPromptBuilder promptBuilder;

    @Override
    public List<String> suggestImprovements(String weakQuery, int score) {
        String prompt = promptBuilder.build(weakQuery, score);
        String json = openAiService.classify(prompt);

        if (json == null || json.isBlank()) {
            log.warn("OpenAiSerpQueryImprover: empty response from OpenAI for query='{}'", weakQuery);
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode queriesNode = root.path("queries");

            if (!queriesNode.isArray()) {
                log.warn("OpenAiSerpQueryImprover: 'queries' is not an array. json='{}'", json);
                return List.of();
            }

            List<String> result = new ArrayList<>();
            for (JsonNode node : queriesNode) {
                String q = node.asText("").trim();
                if (!q.isBlank()) {
                    result.add(q);
                }
            }

            log.info("OpenAiSerpQueryImprover: got {} suggestions for query='{}'",
                    result.size(), weakQuery);
            return result;

        } catch (Exception e) {
            log.warn("OpenAiSerpQueryImprover: failed to parse JSON response. json='{}'", json, e);
            return List.of();
        }
    }
}