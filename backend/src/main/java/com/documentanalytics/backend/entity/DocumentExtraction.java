
package com.documentanalytics.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "document_extractions")
public class DocumentExtraction {

    @Id
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(
        name = "extracted_text",
        nullable = false,
        columnDefinition = "TEXT"
    )
    private String extractedText;

    @Column(name = "character_count", nullable = false)
    private Integer characterCount;

    @Column(name = "extracted_at", nullable = false)
    private OffsetDateTime extractedAt;

    protected DocumentExtraction() {
    }

    public DocumentExtraction(
            Long documentId,
            String extractedText
    ) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                "A valid document ID is required."
            );
        }

        setExtractedText(extractedText);
        this.documentId = documentId;
    }

    @PrePersist
    protected void beforeInsert() {
        if (extractedAt == null) {
            extractedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public Integer getCharacterCount() {
        return characterCount;
    }

    public OffsetDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedText(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException(
                "Extracted text cannot be empty."
            );
        }

        String normalizedText = extractedText.trim();

        if (normalizedText.length() > 2_000_000) {
            throw new IllegalArgumentException(
                "Extracted text exceeds the maximum length."
            );
        }

        this.extractedText = normalizedText;
        this.characterCount = normalizedText.length();
    }
}
