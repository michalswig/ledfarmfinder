package com.mike.leadfarmfinder.service.directory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "hofladenfinder")
public record HofladenFinderProperties(

        String baseUrl,
        int maxPagesPerProduct,
        List<String> products
) {

    public HofladenFinderProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.hofladenfinder.org/farmshop/search";
        }
        if (maxPagesPerProduct <= 0) {
            maxPagesPerProduct = 200;
        }
        if (products == null || products.isEmpty()) {
            products = List.of(
                    "spargel",
                    "erdbeeren",
                    "kartoffeln",
                    "tomaten",
                    "gurken",
                    "aepfel",
                    "kirschen",
                    "heidelbeeren",
                    "kuerbis",
                    "blumen",
                    "gemuese",
                    "zwiebeln"
            );
        }
    }
}