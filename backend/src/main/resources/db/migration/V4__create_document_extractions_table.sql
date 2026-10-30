
-- ===============================================
-- V4: Store extracted text from uploaded documents
-- Intelligent Document Analytics Hub
-- ===============================================

CREATE TABLE document_extractions (

    -- Each document has one current extraction record.
    document_id BIGINT PRIMARY KEY,

    -- Extracted readable text from PDF, DOCX, or TXT.
    extracted_text TEXT NOT NULL,

    -- Number of characters recorded by the extractor.
    character_count INTEGER NOT NULL,

    -- Time when extraction was saved.
    extracted_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    -- Link extracted text to its original document.
    CONSTRAINT fk_extraction_document
        FOREIGN KEY (document_id)
        REFERENCES documents(id)
        ON DELETE CASCADE,

    -- Prevent invalid character counts.
    CONSTRAINT chk_extraction_character_count
        CHECK (
            character_count > 0
            AND character_count <= 2000000
        ),

    -- Prevent empty extracted content.
    CONSTRAINT chk_extraction_nonempty
        CHECK (
            length(btrim(extracted_text)) > 0
        )
);
