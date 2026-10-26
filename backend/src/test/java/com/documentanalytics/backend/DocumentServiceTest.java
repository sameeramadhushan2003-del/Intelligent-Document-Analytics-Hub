
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.service.DocumentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Test
    void shouldCreateAndFindDocumentMetadata() {

        Document document = documentService.createMetadata(
                "Control Systems Lecture",
                "control_systems.pdf",
                "application/pdf",
                500000
        );

        assertNotNull(document.getId());

        Document found = documentService
                .findById(document.getId())
                .orElseThrow();

        assertEquals(
                "Control Systems Lecture",
                found.getTitle()
        );

        assertEquals(
                500000L,
                found.getFileSizeBytes()
        );
    }

    @Test
    void shouldRejectEmptyTitle() {

        assertThrows(
                IllegalArgumentException.class,
                () -> documentService.createMetadata(
                        "",
                        "lecture.pdf",
                        "application/pdf",
                        500000
                )
        );
    }

    @Test
    void shouldRejectNegativeFileSize() {

        assertThrows(
                IllegalArgumentException.class,
                () -> documentService.createMetadata(
                        "Lecture Notes",
                        "lecture.pdf",
                        "application/pdf",
                        -100
                )
        );
    }
}
