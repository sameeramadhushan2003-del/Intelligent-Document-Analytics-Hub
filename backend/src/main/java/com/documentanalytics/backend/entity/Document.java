
package com.documentanalytics.backend.entity;
import jakarta.persistence.Column;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private DocumentStatus processingStatus =
            DocumentStatus.UPLOADED;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "storage_key", length = 120)
    private String storageKey;

    @Column(name = "sha256_hex", length = 64)
    private String sha256Hex;


    public Document() {
        // Required by JPA
    }

    public Document(
            String title,
            String originalFilename,
            String contentType,
            long fileSizeBytes
    ) {
        this.title = title;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
    }

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public DocumentStatus getProcessingStatus() {
        return processingStatus;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public void setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
    }

    
    public void setTitle(String title) {
        this.title = title;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public void setProcessingStatus(DocumentStatus processingStatus) {
        this.processingStatus = processingStatus;
    }


}
