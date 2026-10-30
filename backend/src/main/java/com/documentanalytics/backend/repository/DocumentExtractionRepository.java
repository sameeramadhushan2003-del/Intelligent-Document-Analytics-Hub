package com.documentanalytics.backend.repository;

import com.documentanalytics.backend.entity.DocumentExtraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentExtractionRepository
        extends JpaRepository<DocumentExtraction, Long> {
}