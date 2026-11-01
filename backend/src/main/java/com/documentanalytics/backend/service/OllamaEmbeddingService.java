
package com.documentanalytics.backend.service;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OllamaEmbeddingService {

    private static final String MODEL_NAME = "all-minilm";
    private static final int EMBEDDING_DIMENSIONS = 384;
    private static final Logger logger =
            LoggerFactory.getLogger(OllamaEmbeddingService.class);
    private static final Semaphore EMBEDDING_PERMIT = new Semaphore(1, true);
    private static final long QUEUE_WAIT_SECONDS = 45;

    private final RestClient restClient;

    public OllamaEmbeddingService(
            @Value("${app.ollama.base-url:http://localhost:11434}")
            String ollamaBaseUrl,
            @Value("${app.ollama.connect-timeout:3s}")
            Duration connectTimeout,
            @Value("${app.ollama.read-timeout:45s}")
            Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .requestFactory(requestFactory)
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

        try {
            if (!EMBEDDING_PERMIT.tryAcquire(
                    QUEUE_WAIT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Embedding service is busy. Please retry shortly."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Embedding request was interrupted. Please retry.",
                    exception
            );
        }

        try {
            return requestEmbedding(text);
        } finally {
            EMBEDDING_PERMIT.release();
        }
    }

    private float[] requestEmbedding(String text) {
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

        } catch (RestClientResponseException exception) {
            int upstreamStatus = exception.getStatusCode().value();
            logger.warn(
                    "Ollama embedding request returned HTTP {}.",
                    upstreamStatus
            );
            if (upstreamStatus == 404) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The configured embedding model is unavailable.",
                        exception
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The embedding service rejected the request.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                logger.warn("Ollama embedding request timed out.");
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "The embedding service timed out.",
                        exception
                );
            }
            logger.warn(
                    "Ollama embedding service is unreachable ({}).",
                    exception.getClass().getSimpleName()
            );
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The embedding service is unavailable.",
                    exception
            );
        } catch (RestClientException exception) {
            logger.warn(
                    "Ollama embedding request failed ({}).",
                    exception.getClass().getSimpleName()
            );
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The embedding service is unavailable.",
                    exception
            );
        }

        if (response == null ||
                !(response.get("embeddings") instanceof List<?> embeddings) ||
                embeddings.size() != 1 ||
                !(embeddings.get(0) instanceof List<?> values)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The embedding service returned an invalid response."
            );
        }

        if (values.size() != EMBEDDING_DIMENSIONS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The embedding service returned an unexpected vector size."
            );
        }

        float[] result = new float[EMBEDDING_DIMENSIONS];

        for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {

            if (!(values.get(i) instanceof Number number)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The embedding service returned an invalid vector."
                );
            }

            float value = number.floatValue();

            if (!Float.isFinite(value)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The embedding service returned an invalid vector."
                );
            }

            result[i] = value;
        }

        return result;
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
