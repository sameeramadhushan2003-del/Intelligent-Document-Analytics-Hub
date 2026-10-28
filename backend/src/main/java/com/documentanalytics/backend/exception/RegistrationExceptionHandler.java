
package com.documentanalytics.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RegistrationExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail() {

        return ResponseEntity.status(409).body(
                Map.of("message", "Email is already registered")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " +
                        error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid registration details");

        return ResponseEntity.badRequest().body(
                Map.of("message", message)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                Map.of("message", exception.getMessage())
        );
    }
}
