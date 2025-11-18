package com.mike.leadfarmfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAiService {
    private final OpenAiProperties openAiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiService(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.restClient = RestClient.builder()
                .baseUrl(openAiProperties.baseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    public String classify(String prompt) {
        try{
            log.info("OpenAiClient.classify: sending prompt to OpenAI, length={}", prompt.length());

            Map<String, Object> requestBody = Map.of(
                    "model", openAiProperties.model(),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You are a strict JSON-only classifier. " +
                                            "Always respond with a single JSON object, no explanation, no markdown."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "temperature", 0
            );

            // surowa odpowiedź OpenAI jako String
            String rawResponse = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + openAiProperties.apiKey())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.debug("OpenAiClient.classify: rawResponse={}", rawResponse);

            // wyciągamy choices[0].message.content
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("OpenAiClient.classify: no choices in response");
                return "";
            }

            JsonNode firstChoice = choices.get(0);
            String content = firstChoice.path("message").path("content").asText("");

            log.info("OpenAiClient.classify: extracted content length={}", content.length());
            log.debug("OpenAiClient.classify: content='{}'",
                    content.substring(0, Math.min(300, content.length())));

            return content;
        } catch (Exception e) {
            log.error("OpenAiClient.classify: error while calling OpenAI", e);
            return "";
        }
    }
}
