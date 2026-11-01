package com.documentanalytics.backend.exception;

import com.documentanalytics.backend.controller.DocumentLibraryController;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DocumentLibraryController.class)
public class DocumentLibraryExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentLibraryExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(
            ResponseStatusException exception
    ) {
        int status = exception.getStatusCode().value();
        String message = switch (status) {
            case 400 -> "The document request is invalid.";
            case 401 -> "Your session has ended. Sign in again.";
            case 403 -> "You do not have permission to change this document.";
            case 404 -> "This document is no longer available. Refresh your library.";
            case 409 -> "The document cannot be deleted while it is being changed. Retry shortly.";
            default -> "The document request could not be completed. Please retry.";
        };
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(
                Map.of("message", "The document request is invalid.")
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        logger.error(
                "Document library database operation failed ({}).",
                exception.getClass().getSimpleName(),
                exception
        );
        String message = "PATCH".equalsIgnoreCase(request.getMethod())
                ? "The document title could not be saved. Please retry shortly."
                : "The document could not be removed from the library. No deletion was confirmed; retry shortly.";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "message", message
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> storage(
            IllegalStateException exception
    ) {
        logger.error(
                "Document library storage operation failed ({}).",
                exception.getClass().getSimpleName(),
                exception
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "message",
                        "Document deletion could not be completed safely. Refresh your library and retry."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        logger.error(
                "Unexpected document library operation failure ({}).",
                exception.getClass().getSimpleName(),
                exception
        );
        return ResponseEntity.internalServerError()
                .body(Map.of(
                        "message",
                        "The document request could not be completed. Please retry."
                ));
    }
}
