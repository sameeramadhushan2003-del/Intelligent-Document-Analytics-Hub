
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.repository.AppUserRepository;
import com.documentanalytics.backend.repository.DocumentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DocumentLibraryService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository userRepository;

    public DocumentLibraryService(
            DocumentRepository documentRepository,
            AppUserRepository userRepository
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    // Check that the authenticated account is still active.
    private void validateUser(Long authenticatedUserId) {

        if (authenticatedUserId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        AppUser user = userRepository
                .findById(authenticatedUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid user account."
                        )
                );

        if (!user.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is inactive."
            );
        }
    }

    // Retrieve only documents belonging to this user.
    @Transactional(readOnly = true)
    public List<Document> getMyDocuments(
            Long authenticatedUserId
    ) {

        validateUser(authenticatedUserId);

        return documentRepository
                .findByOwnerUserIdOrderByUploadedAtDesc(
                        authenticatedUserId
                );
    }

    // Retrieve one document only if the user owns it.
    @Transactional(readOnly = true)
    public Document getMyDocument(
            Long documentId,
            Long authenticatedUserId
    ) {

        validateUser(authenticatedUserId);

        if (documentId == null || documentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found."
            );
        }

        return documentRepository
                .findByIdAndOwnerUserId(
                        documentId,
                        authenticatedUserId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Document not found."
                        )
                );
    }
}
