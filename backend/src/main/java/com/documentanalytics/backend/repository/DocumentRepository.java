
package com.documentanalytics.backend.repository;

import com.documentanalytics.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository
        extends JpaRepository<Document, Long> {

    List<Document> findByOwnerUserIdOrderByUploadedAtDesc(
            Long ownerUserId
    );

    Optional<Document> findByIdAndOwnerUserId(
            Long id,
            Long ownerUserId
    );
}
