
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.entity.DocumentStatus;
import com.documentanalytics.backend.repository.DocumentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldSaveAndRetrieveDocument() {

        Document document = new Document(
            "Machine Learning Lecture",
            "ml_lecture.pdf",
            "application/pdf",
            250000
        );

        Document savedDocument =
                documentRepository.saveAndFlush(document);

        assertNotNull(savedDocument.getId());

        entityManager.clear();

        Document retrievedDocument = documentRepository
                .findById(savedDocument.getId())
                .orElseThrow();

        assertEquals(
            "Machine Learning Lecture",
            retrievedDocument.getTitle()
        );

        assertEquals(
            DocumentStatus.UPLOADED,
            retrievedDocument.getProcessingStatus()
        );

        assertNotNull(retrievedDocument.getUploadedAt());
    }

    @Test
    void changingTitlePersistsWithoutChangingOriginalFileOrProcessingMetadata() {
        Document document = new Document(
                "Original title",
                "unchanged-original.pdf",
                "application/pdf",
                250000
        );
        document.setStorageKey("synthetic-storage-key");
        document.setSha256Hex("synthetic-hash");
        Document saved = documentRepository.saveAndFlush(document);

        saved.setTitle("Updated title");
        documentRepository.saveAndFlush(saved);
        entityManager.clear();

        Document updated = documentRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Updated title", updated.getTitle());
        assertEquals("unchanged-original.pdf", updated.getOriginalFilename());
        assertEquals("application/pdf", updated.getContentType());
        assertEquals(250000L, updated.getFileSizeBytes());
        assertEquals("synthetic-storage-key", updated.getStorageKey());
        assertEquals("synthetic-hash", updated.getSha256Hex());
        assertEquals(DocumentStatus.UPLOADED, updated.getProcessingStatus());
    }
}
