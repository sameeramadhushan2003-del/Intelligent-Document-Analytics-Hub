
package com.documentanalytics.backend.repository;

import com.documentanalytics.backend.entity.Document;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id and d.ownerUserId = :ownerUserId")
    Optional<Document> findByIdAndOwnerUserIdForUpdate(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId
    );
}
