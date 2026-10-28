
package com.documentanalytics.backend.dto;

import java.time.Instant;

public record RegisterResponse(
    Long id,
    String fullName,
    String email,
    String role,
    Instant createdAt
) {}
