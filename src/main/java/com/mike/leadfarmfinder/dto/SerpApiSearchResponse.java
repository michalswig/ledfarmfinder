package com.mike.leadfarmfinder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SerpApiSearchResponse(
        @JsonProperty("organic_results")
        List<OrganicResult> organicResults
) {
}
