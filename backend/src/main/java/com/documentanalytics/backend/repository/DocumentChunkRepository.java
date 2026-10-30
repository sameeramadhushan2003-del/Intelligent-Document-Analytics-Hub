
package com.documentanalytics.backend.repository;

import com.documentanalytics.backend.entity.DocumentChunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository
        extends JpaRepository<DocumentChunk, Long> {

    // Retrieve all chunks belonging to a document
    // in their original order.
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(
            Long documentId
    );

    // Count how many chunks belong to a document.
    long countByDocumentId(Long documentId);

}
