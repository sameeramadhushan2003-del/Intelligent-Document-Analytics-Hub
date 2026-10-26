
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.repository.DocumentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    public Document createMetadata(
            String title,
            String originalFilename,
            String contentType,
            long fileSizeBytes
    ) {
        String validTitle =
                requireText(title, "Title", 255);

        String validFilename =
                requireText(originalFilename, "Filename", 255);

        String validContentType =
                requireText(contentType, "Content type", 120);

        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException(
                    "File size cannot be negative"
            );
        }

        Document document = new Document(
                validTitle,
                validFilename,
                validContentType,
                fileSizeBytes
        );

        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public Optional<Document> findById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Document ID must be positive"
            );
        }

        return documentRepository.findById(id);
    }

    private String requireText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be empty"
            );
        }

        String cleaned = value.trim();

        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds " + maxLength + " characters"
            );
        }

        return cleaned;
    }
}
