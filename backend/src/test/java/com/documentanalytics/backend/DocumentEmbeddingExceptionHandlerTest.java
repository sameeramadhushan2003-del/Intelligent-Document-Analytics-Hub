package com.documentanalytics.backend;

import com.documentanalytics.backend.exception.DocumentEmbeddingExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentEmbeddingExceptionHandlerTest {

    private final DocumentEmbeddingExceptionHandler handler =
            new DocumentEmbeddingExceptionHandler();

    @Test
    void mapsMissingChunksToSafeConflictMessage() {
        var response = handler.status(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No document chunks found."
        ));

        assertEquals(409, response.getStatusCode().value());
        assertEquals(
                "Generate document chunks before generating embeddings.",
                response.getBody().get("message")
        );
    }

    @Test
    void mapsUpstreamTimeoutWithoutExposingExceptionDetails() {
        var response = handler.status(new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "synthetic timeout detail"
        ));

        assertEquals(504, response.getStatusCode().value());
        assertEquals(
                "Embedding generation timed out. Existing embeddings are preserved; please retry.",
                response.getBody().get("message")
        );
    }

    @Test
    void mapsDatabaseConnectivityFailureToSafeUnavailableMessage() {
        var response = handler.database(
                new DataAccessResourceFailureException("synthetic database detail")
        );

        assertEquals(503, response.getStatusCode().value());
        assertEquals(
                "The database is temporarily unavailable. Existing embeddings are preserved; please retry.",
                response.getBody().get("message")
        );
    }
}
