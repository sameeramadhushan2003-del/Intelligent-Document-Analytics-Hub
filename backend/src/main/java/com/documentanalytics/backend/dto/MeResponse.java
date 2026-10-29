
package com.documentanalytics.backend.dto;

public record MeResponse(
        Long id,
        String fullName,
        String email,
        String role
) {}
