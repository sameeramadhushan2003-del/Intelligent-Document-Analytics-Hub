package com.documentanalytics.backend;

import com.documentanalytics.backend.service.OllamaEmbeddingService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class OllamaEmbeddingServiceTest {

    @Test
    void parsesA384DimensionalAllMiniLmVectorFromSyntheticResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String vector = String.join(",", java.util.Collections.nCopies(384, "0.125"));
        String response = "{\"embeddings\":[[" + vector + "]]}";
        withServer(200, response, 0, (body) -> requestBody.set(body), port -> {
            var service = service(port, Duration.ofSeconds(2));
            float[] embedding = service.generateEmbedding("Synthetic test sentence.");
            assertEquals(384, embedding.length);
            assertTrue(java.util.Arrays.stream(toDoubleArray(embedding))
                    .allMatch(Double::isFinite));
            assertTrue(requestBody.get().contains("\"model\":\"all-minilm\""));
        });
    }

    @Test
    void rejectsWrongVectorDimensionsAsBadGateway() throws Exception {
        String response = "{\"embeddings\":[[0.1,0.2]]}";
        withServer(200, response, 0, ignored -> {}, port -> {
            var exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service(port, Duration.ofSeconds(2))
                            .generateEmbedding("Synthetic text.")
            );
            assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
        });
    }

    @Test
    void mapsMissingOllamaModelToServiceUnavailable() throws Exception {
        withServer(404, "{\"error\":\"model not found\"}", 0, ignored -> {}, port -> {
            var exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service(port, Duration.ofSeconds(2))
                            .generateEmbedding("Synthetic text.")
            );
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        });
    }

    @Test
    void mapsOllamaTimeoutToGatewayTimeout() throws Exception {
        withServer(200, "{\"embeddings\":[[]]}", 500, ignored -> {}, port -> {
            var exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service(port, Duration.ofMillis(120))
                            .generateEmbedding("Synthetic text.")
            );
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatusCode());
        });
    }

    @Test
    void mapsUnreachableOllamaToServiceUnavailable() {
        var exception = assertThrows(
                ResponseStatusException.class,
                () -> new OllamaEmbeddingService(
                        "http://127.0.0.1:1",
                        Duration.ofMillis(150),
                        Duration.ofMillis(250)
                ).generateEmbedding("Synthetic text.")
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void shouldRejectEmptyText() {
        assertThrows(IllegalArgumentException.class,
                () -> service("http://localhost:11434").generateEmbedding(""));
    }

    @Test
    void shouldRejectBlankText() {
        assertThrows(IllegalArgumentException.class,
                () -> service("http://localhost:11434").generateEmbedding("   "));
    }

    @Test
    void shouldRejectTextThatIsTooLong() {
        String longText = "a".repeat(10001);
        assertThrows(IllegalArgumentException.class,
                () -> service("http://localhost:11434").generateEmbedding(longText));
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "RUN_OLLAMA_INTEGRATION_TEST",
            matches = "true"
    )
    void shouldGenerate384DimensionalEmbeddingFromLocalOllama() {
        float[] embedding = service("http://localhost:11434")
                .generateEmbedding("Synthetic health-check sentence.");

        assertEquals(384, embedding.length);
        for (float value : embedding) {
            assertTrue(Float.isFinite(value));
        }
        assertEquals("all-minilm", service("http://localhost:11434").getModelName());
    }

    private OllamaEmbeddingService service(int port, Duration timeout) {
        return new OllamaEmbeddingService(
                "http://127.0.0.1:" + port,
                Duration.ofSeconds(2),
                timeout
        );
    }

    private OllamaEmbeddingService service(String url) {
        return new OllamaEmbeddingService(
                url,
                Duration.ofSeconds(3),
                Duration.ofSeconds(45)
        );
    }

    private void withServer(
            int status,
            String body,
            long delayMillis,
            BodyConsumer consumer,
            ServerConsumer test
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            try {
                consumer.accept(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                ));
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // A timed-out client can close the synthetic response early.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            test.accept(server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    private double[] toDoubleArray(float[] values) {
        double[] converted = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            converted[i] = values[i];
        }
        return converted;
    }

    @FunctionalInterface
    private interface BodyConsumer {
        void accept(String body);
    }

    @FunctionalInterface
    private interface ServerConsumer {
        void accept(int port) throws Exception;
    }
}
