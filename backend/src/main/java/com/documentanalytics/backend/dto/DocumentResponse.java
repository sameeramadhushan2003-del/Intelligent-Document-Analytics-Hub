
package com.documentanalytics.backend.dto;

public record DocumentResponse(
        Long id,
        String title,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String processingStatus,
        String uploadedAt
) {}
