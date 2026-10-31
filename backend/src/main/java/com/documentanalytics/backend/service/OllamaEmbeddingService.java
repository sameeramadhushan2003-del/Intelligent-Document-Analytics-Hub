
package com.documentanalytics.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OllamaEmbeddingService {

    private static final String MODEL_NAME = "all-minilm";
    private static final int EMBEDDING_DIMENSIONS = 384;

    private final RestClient restClient;

    public OllamaEmbeddingService(
            @Value("${app.ollama.base-url:http://localhost:11434}")
            String ollamaBaseUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    public String getModelName() {
        return MODEL_NAME;
    }

    public float[] generateEmbedding(String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Text must not be empty."
            );
        }

        if (text.length() > 10000) {
            throw new IllegalArgumentException(
                    "Text is too long for this embedding service."
            );
        }

        Map<?, ?> response;

        try {
            response = restClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", MODEL_NAME,
                            "input", text,
                            "truncate", false
                    ))
                    .retrieve()
                    .body(Map.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Unable to generate embedding. " +
                    "Check that Ollama is running and " +
                    MODEL_NAME + " is installed.",
                    exception
            );
        }

        if (response == null ||
                !(response.get("embeddings") instanceof List<?> embeddings) ||
                embeddings.size() != 1 ||
                !(embeddings.get(0) instanceof List<?> values)) {

            throw new IllegalStateException(
                    "Invalid embedding response from Ollama."
            );
        }

        if (values.size() != EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Expected 384 embedding dimensions, received " +
                    values.size()
            );
        }

        float[] result = new float[EMBEDDING_DIMENSIONS];

        for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {

            if (!(values.get(i) instanceof Number number)) {
                throw new IllegalStateException(
                        "Invalid embedding value at index " + i
                );
            }

            float value = number.floatValue();

            if (!Float.isFinite(value)) {
                throw new IllegalStateException(
                        "Non-finite embedding value at index " + i
                );
            }

            result[i] = value;
        }

        return result;
    }
}
