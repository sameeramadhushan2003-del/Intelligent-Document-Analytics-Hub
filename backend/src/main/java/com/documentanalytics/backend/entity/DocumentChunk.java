
package com.documentanalytics.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "document_chunks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_document_chunks_document_index",
            columnNames = {"document_id", "chunk_index"}
        )
    }
)
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(
        name = "chunk_text",
        nullable = false,
        columnDefinition = "TEXT"
    )
    private String chunkText;

    @Column(name = "start_offset", nullable = false)
    private Integer startOffset;

    @Column(name = "end_offset", nullable = false)
    private Integer endOffset;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Required by JPA.
    protected DocumentChunk() {
    }

    public DocumentChunk(
            Long documentId,
            Integer chunkIndex,
            String chunkText,
            Integer startOffset,
            Integer endOffset
    ) {

        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                "A valid document ID is required."
            );
        }

        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException(
                "Chunk index must be zero or greater."
            );
        }

        if (chunkText == null || chunkText.isBlank()) {
            throw new IllegalArgumentException(
                "Chunk text cannot be empty."
            );
        }

        if (startOffset == null || startOffset < 0) {
            throw new IllegalArgumentException(
                "Start offset must be zero or greater."
            );
        }

        if (endOffset == null || endOffset <= startOffset) {
            throw new IllegalArgumentException(
                "End offset must be greater than start offset."
            );
        }

        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
