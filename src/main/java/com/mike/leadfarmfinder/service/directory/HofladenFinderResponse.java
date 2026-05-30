package com.mike.leadfarmfinder.service.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HofladenFinderResponse(

        @JsonProperty("Locations")
        List<HofladenFinderLocation> locations,

        @JsonProperty("TotalCount")
        int totalCount
) {
    @Override
    public List<HofladenFinderLocation> locations() {
        return locations != null ? locations : List.of();
    }
}