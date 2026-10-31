
package com.documentanalytics.backend;

import com.documentanalytics.backend.service.OllamaEmbeddingService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

class OllamaEmbeddingServiceTest {

    private final OllamaEmbeddingService service =
            new OllamaEmbeddingService("http://localhost:11434");

    @Test
    void shouldRejectEmptyText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.generateEmbedding("")
        );
    }

    @Test
    void shouldRejectBlankText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.generateEmbedding("   ")
        );
    }

    @Test
    void shouldRejectTextThatIsTooLong() {
        String longText = "a".repeat(10001);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.generateEmbedding(longText)
        );
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_OLLAMA_INTEGRATION_TEST",
            matches = "true"
    )
    void shouldGenerate384DimensionalEmbedding() {

        String text =
                "The hospital provides medical services.";

        float[] embedding = service.generateEmbedding(text);

        assertNotNull(embedding);
        assertEquals(384, embedding.length);

        double squaredMagnitude = 0;

        for (float value : embedding) {
            assertTrue(Float.isFinite(value));

            squaredMagnitude += (double) value * value;
        }

        assertTrue(squaredMagnitude > 0);

        assertEquals("all-minilm", service.getModelName());
    }
}
