package com.mike.leadfarmfinder.service.serpquery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SerpQueryGeneratorTest {

    private SerpQueryGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SerpQueryGenerator();
    }

    @Nested
    @DisplayName("generate")
    class GenerateTests {

        @Test
        @DisplayName("should generate non-blank query")
        void shouldGenerateNonBlankQuery() {
            String query = generator.generate( 0, Set.of());
            assertThat(query).isNotBlank();
        }

        @Test
        @DisplayName("should generate different queries for different indices")
        void shouldGenerateDifferentQueriesForDifferentIndices() {
            Set<String> generated = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                generated.add(generator.generate( i, Set.of()));
            }
            assertThat(generated.size()).isGreaterThan(20);
        }

        @Test
        @DisplayName("should use fallback region on collision")
        void shouldUseFallbackRegionOnCollision() {
            String first = generator.generate(0, Set.of());
            String second = generator.generate( 0, Set.of(first));
            assertThat(second).isNotEqualTo(first);
        }

        @Test
        @DisplayName("should use all products across full cycle")
        void shouldRotateThroughAllProducts() {
            Set<String> generated = new HashSet<>();
            for (int i = 0; i < SerpQueryGenerator.PRODUCTS.size(); i++) {
                generated.add(generator.generate(i, Set.of()));
            }
            // Każdy produkt powinien pojawić się dokładnie raz w pełnym cyklu
            for (String product : SerpQueryGenerator.PRODUCTS) {
                boolean anyContains = generated.stream().anyMatch(q -> q.contains(product));
                assertThat(anyContains).as("Product missing from cycle: " + product).isTrue();
            }
        }

        @Test
        @DisplayName("should always contain intent signal")
        void shouldContainIntentSignal() {
            for (int i = 0; i < 20; i++) {
                String q = generator.generate( i, Set.of());
                boolean hasIntent = q.contains("Kontakt") || q.contains("Adresse") || q.contains("Telefon");
                assertThat(hasIntent).as("Missing intent in: " + q).isTrue();
            }
        }

        @Test
        @DisplayName("should always contain structural filter")
        void shouldContainStructuralFilter() {
            for (int i = 0; i < 20; i++) {
                String q = generator.generate(i, Set.of());
                boolean hasFilter = SerpQueryGenerator.IMPRESSUM_FILTERS.stream().anyMatch(q::contains)
                        || SerpQueryGenerator.FIRST_PERSON_FILTERS.stream().anyMatch(q::contains)
                        || SerpQueryGenerator.OPERATIONAL_FILTERS.stream().anyMatch(q::contains);
                assertThat(hasFilter).as("Missing structural filter in: " + q).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("generateBatch")
    class GenerateBatchTests {

        @Test
        @DisplayName("should generate requested count of unique queries")
        void shouldGenerateUniqueQueries() {
            List<String> batch = generator.generateBatch(50, Set.of());
            assertThat(batch).hasSize(50);
            assertThat(new HashSet<>(batch)).hasSize(50);
        }

        @Test
        @DisplayName("should not include queries from existing set")
        void shouldNotIncludeExistingQueries() {
            List<String> first = generator.generateBatch(10, Set.of());
            Set<String> existing = new HashSet<>(first);
            List<String> second = generator.generateBatch(10, existing);
            for (String q : second) {
                assertThat(existing).doesNotContain(q);
            }
        }
    }
}