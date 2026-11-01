package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.repository.DocumentRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DocumentDeletionService {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentDeletionService.class);

    private final DocumentLibraryService documentLibraryService;
    private final DocumentRepository documentRepository;
    private final PrivateFileStorageService storageService;

    public DocumentDeletionService(
            DocumentLibraryService documentLibraryService,
            DocumentRepository documentRepository,
            PrivateFileStorageService storageService
    ) {
        this.documentLibraryService = documentLibraryService;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    @Transactional
    public DeletionOutcome deleteMyDocument(
            Long documentId,
            Long authenticatedUserId
    ) {
        Document document = documentLibraryService.getMyDocumentForUpdate(
                documentId,
                authenticatedUserId
        );

        PrivateFileStorageService.StagedFile stagedFile =
                storageService.stageStoredFileDeletion(document.getStorageKey());
        DeletionOutcome outcome = new DeletionOutcome();
        try {
            documentRepository.delete(document);
            documentRepository.flush();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                storageService.completeStagedFileDeletion(stagedFile);
                            } catch (RuntimeException exception) {
                                outcome.markFilesystemCleanupPending();
                                logger.error(
                                        "Filesystem cleanup remains pending after document metadata deletion. documentId={}",
                                        documentId,
                                        exception
                                );
                            }
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                restoreAfterFailure(stagedFile, null);
                            }
                        }
                    }
            );
        } catch (RuntimeException exception) {
            logger.error(
                    "Document deletion failed before transaction completion. documentId={}",
                    documentId,
                    exception
            );
            restoreAfterFailure(stagedFile, exception);
            throw exception;
        }
        return outcome;
    }

    public static final class DeletionOutcome {
        private final AtomicBoolean filesystemCleanupPending = new AtomicBoolean();

        private void markFilesystemCleanupPending() {
            filesystemCleanupPending.set(true);
        }

        public boolean isFilesystemCleanupPending() {
            return filesystemCleanupPending.get();
        }
    }

    private void restoreAfterFailure(
            PrivateFileStorageService.StagedFile stagedFile,
            RuntimeException originalFailure
    ) {
        try {
            storageService.restoreStagedFile(stagedFile);
        } catch (RuntimeException restoreFailure) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(restoreFailure);
            } else {
                throw restoreFailure;
            }
        }
    }
}
