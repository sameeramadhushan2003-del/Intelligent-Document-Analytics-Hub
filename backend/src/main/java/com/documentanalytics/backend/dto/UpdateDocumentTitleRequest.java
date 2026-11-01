package com.documentanalytics.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentTitleRequest(
        @NotBlank
        @Size(max = 255)
        String title
) {
}
