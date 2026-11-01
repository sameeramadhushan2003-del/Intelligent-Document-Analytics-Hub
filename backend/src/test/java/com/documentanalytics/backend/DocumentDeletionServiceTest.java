package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.repository.DocumentRepository;
import com.documentanalytics.backend.service.DocumentDeletionService;
import com.documentanalytics.backend.service.DocumentLibraryService;
import com.documentanalytics.backend.service.PrivateFileStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentDeletionServiceTest {
    @TempDir Path storageRoot;

    private DocumentLibraryService library;
    private DocumentRepository documents;
    private PrivateFileStorageService storage;
    private DocumentDeletionService service;

    @BeforeEach
    void setUp() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        library = mock(DocumentLibraryService.class);
        documents = mock(DocumentRepository.class);
        storage = new PrivateFileStorageService(storageRoot.toString());
        service = new DocumentDeletionService(library, documents, storage);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Document storedDocument() {
        String storageKey = UUID.randomUUID().toString();
        var document = mock(Document.class);
        when(document.getStorageKey()).thenReturn(storageKey);
        try {
            Files.writeString(storageRoot.resolve(storageKey), "Synthetic test document");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        when(library.getMyDocumentForUpdate(79L, 7L)).thenReturn(document);
        return document;
    }

    @Test
    void ownerDeletionStagesFileUntilDatabaseCommitThenRemovesIt() {
        Document document = storedDocument();

        var outcome = service.deleteMyDocument(79L, 7L);

        verify(library).getMyDocumentForUpdate(79L, 7L);
        verify(documents).delete(document);
        verify(documents).flush();
        assertEquals(1, countPendingFiles());
        assertFalse(Files.exists(storageRoot.resolve(storageKey(document))));

        runSynchronizations(TransactionSynchronization.STATUS_COMMITTED, true);

        assertEquals(0, countPendingFiles());
        assertFalse(outcome.isFilesystemCleanupPending());
    }

    @Test
    void simulatedForeignKeyConstraintFailureRestoresOriginalFileAndDoesNotLeaveStagedFile() {
        Document document = storedDocument();
        doThrow(new DataIntegrityViolationException("synthetic foreign key failure"))
                .when(documents).flush();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.deleteMyDocument(79L, 7L)
        );

        assertTrue(Files.exists(storageRoot.resolve(storageKey(document))));
        assertEquals(0, countPendingFiles());
    }

    @Test
    void transactionRollbackAfterDeleteFlushRestoresOriginalFile() {
        Document document = storedDocument();

        service.deleteMyDocument(79L, 7L);
        assertEquals(1, countPendingFiles());

        runSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK, false);

        assertTrue(Files.exists(storageRoot.resolve(storageKey(document))));
        assertEquals(0, countPendingFiles());
    }

    @Test
    void postCommitStorageFailureLeavesOnlyQuarantinedFileForRecovery() {
        Document document = storedDocument();
        PrivateFileStorageService failingStorage = spy(storage);
        doThrow(new IllegalStateException("synthetic storage failure"))
                .when(failingStorage).completeStagedFileDeletion(any());
        service = new DocumentDeletionService(library, documents, failingStorage);

        var outcome = service.deleteMyDocument(79L, 7L);
        runSynchronizations(TransactionSynchronization.STATUS_COMMITTED, true);

        assertFalse(Files.exists(storageRoot.resolve(storageKey(document))));
        assertEquals(1, countPendingFiles());
        assertTrue(outcome.isFilesystemCleanupPending());
    }

    @Test
    void missingOrUnownedDocumentNeverTouchesStorageOrDatabaseDeletion() {
        when(library.getMyDocumentForUpdate(79L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found."));

        assertThrows(ResponseStatusException.class, () -> service.deleteMyDocument(79L, 7L));

        verifyNoInteractions(documents);
    }

    @Test
    void fileStagingFailureLeavesDocumentMetadataUntouched() {
        Document document = storedDocument();
        String storageKey = document.getStorageKey();
        PrivateFileStorageService failingStorage = spy(storage);
        doThrow(new IllegalStateException("synthetic storage failure"))
                .when(failingStorage).stageStoredFileDeletion(storageKey);
        service = new DocumentDeletionService(library, documents, failingStorage);

        assertThrows(IllegalStateException.class, () -> service.deleteMyDocument(79L, 7L));

        verifyNoInteractions(documents);
        assertTrue(Files.exists(storageRoot.resolve(storageKey)));
    }

    private String storageKey(Document document) {
        return document.getStorageKey();
    }

    private long countPendingFiles() {
        try (var files = Files.list(storageRoot)) {
            return files.filter(path -> path.getFileName().toString().startsWith(".pending-delete-")).count();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void runSynchronizations(int status, boolean afterCommit) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        for (TransactionSynchronization synchronization : synchronizations) {
            if (afterCommit) synchronization.afterCommit();
            synchronization.afterCompletion(status);
        }
    }
}
