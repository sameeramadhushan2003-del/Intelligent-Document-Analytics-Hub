
-- V3: Add secure document ownership and storage metadata

-- Identify the user who owns each document
ALTER TABLE documents
ADD COLUMN owner_user_id BIGINT;

-- Store an internal unique identifier for the uploaded file
ALTER TABLE documents
ADD COLUMN storage_key VARCHAR(120);

-- Store the SHA-256 checksum of the uploaded file
ALTER TABLE documents
ADD COLUMN sha256_hex CHAR(64);

-- Connect documents to their registered owners
ALTER TABLE documents
ADD CONSTRAINT fk_documents_owner
FOREIGN KEY (owner_user_id)
REFERENCES app_users(id);

-- Prevent duplicate internal storage identifiers
ALTER TABLE documents
ADD CONSTRAINT uq_documents_storage_key
UNIQUE (storage_key);

-- Improve document listing performance for each user
CREATE INDEX idx_documents_owner_uploaded
ON documents (owner_user_id, uploaded_at DESC);
