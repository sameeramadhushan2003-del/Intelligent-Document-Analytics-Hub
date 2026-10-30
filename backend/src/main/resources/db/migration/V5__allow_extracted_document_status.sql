
-- V5: Allow the EXTRACTED document processing status.
-- Preserve all previously supported statuses.

DO $$
DECLARE
    old_constraint_name NAME;
    matching_constraint_count INTEGER;
BEGIN

    -- Find the existing status CHECK constraint.
    SELECT COUNT(*), MAX(conname)
    INTO matching_constraint_count, old_constraint_name
    FROM pg_constraint
    WHERE conrelid = 'public.documents'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%processing_status%';

    -- Avoid changing an unexpected database structure.
    IF matching_constraint_count <> 1 THEN
        RAISE EXCEPTION
            'Expected exactly one processing_status CHECK constraint, found %',
            matching_constraint_count;
    END IF;

    -- Remove only the old status restriction.
    EXECUTE format(
        'ALTER TABLE public.documents DROP CONSTRAINT %I',
        old_constraint_name
    );

END $$;

-- Add the updated restriction.
ALTER TABLE public.documents
ADD CONSTRAINT chk_documents_processing_status
CHECK (
    processing_status IN (
        'UPLOADED',
        'PROCESSING',
        'EXTRACTED',
        'INDEXED',
        'FAILED'
    )
);
