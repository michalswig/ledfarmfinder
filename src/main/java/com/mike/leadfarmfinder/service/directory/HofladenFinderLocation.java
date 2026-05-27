package com.mike.leadfarmfinder.service.directory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HofladenFinderLocation(

        @JsonProperty("Id")
        Long id,

        @JsonProperty("Name")
        String name,

        @JsonProperty("Website")
        String website,

        @JsonProperty("Place")
        String place
) {}