
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.entity.DocumentStatus;
import com.documentanalytics.backend.repository.AppUserRepository;
import com.documentanalytics.backend.repository.DocumentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository userRepository;
    private final PrivateFileStorageService storageService;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            AppUserRepository userRepository,
            PrivateFileStorageService storageService
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    public Document upload(
            MultipartFile file,
            Long authenticatedUserId
    ) {

        // The user ID must come from a verified JWT.
        if (authenticatedUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        // Verify that the account exists and is active.
        AppUser owner = userRepository
                .findById(authenticatedUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid user account."
                        )
                );

        if (!owner.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is inactive."
            );
        }

        // Validate and save the actual document file.
        PrivateFileStorageService.StoredFile storedFile =
                storageService.store(file);

        try {
            // Prepare document metadata.
            Document document = new Document();

            document.setTitle(
                    storedFile.originalFilename()
            );

            document.setOriginalFilename(
                    storedFile.originalFilename()
            );

            document.setContentType(
                    storedFile.contentType()
            );

            document.setFileSizeBytes(
                    storedFile.fileSizeBytes()
            );

            document.setProcessingStatus(
                    DocumentStatus.UPLOADED
            );

            document.setOwnerUserId(
                    owner.getId()
            );

            document.setStorageKey(
                    storedFile.storageKey()
            );

            document.setSha256Hex(
                    storedFile.sha256Hex()
            );

            // Save metadata in PostgreSQL.
            // Without a service-level transaction,
            // this repository operation commits independently.
            return documentRepository.saveAndFlush(document);

        } catch (RuntimeException exception) {

            // If the database operation fails,
            // remove the previously saved file.
            try {
                storageService.deleteStoredFile(
                        storedFile.storageKey()
                );

            } catch (RuntimeException cleanupError) {
                exception.addSuppressed(cleanupError);
            }

            throw exception;
        }
    }
}
