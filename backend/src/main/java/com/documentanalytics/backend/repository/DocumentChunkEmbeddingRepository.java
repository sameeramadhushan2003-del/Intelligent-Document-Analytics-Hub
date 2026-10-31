
package com.documentanalytics.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentChunkEmbeddingRepository {

    private static final int EXPECTED_DIMENSIONS = 384;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkEmbeddingRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveOrUpdateEmbedding(
            Long chunkId,
            String modelName,
            float[] embedding) {

        if (chunkId == null || chunkId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid document chunk ID."
            );
        }

        if (modelName == null ||
                modelName.isBlank() ||
                modelName.length() > 120) {
            throw new IllegalArgumentException(
                    "Invalid embedding model name."
            );
        }

        if (embedding == null ||
                embedding.length != EXPECTED_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Embedding must contain exactly 384 dimensions."
            );
        }

        String vectorValue = toVectorString(embedding);

        String sql = """
                INSERT INTO document_chunk_embeddings (
                    chunk_id,
                    embedding_model,
                    embedding_dimensions,
                    embedding
                )
                VALUES (?, ?, ?, CAST(? AS vector))
                ON CONFLICT (chunk_id, embedding_model)
                DO UPDATE SET
                    embedding_dimensions =
                        EXCLUDED.embedding_dimensions,
                    embedding =
                        EXCLUDED.embedding
                """;

        jdbcTemplate.update(
                sql,
                chunkId,
                modelName.trim(),
                embedding.length,
                vectorValue
        );
    }

    private String toVectorString(float[] embedding) {

        StringBuilder vector = new StringBuilder("[");

        for (int i = 0; i < embedding.length; i++) {

            float value = embedding[i];

            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Embedding contains an invalid value."
                );
            }

            if (i > 0) {
                vector.append(",");
            }

            vector.append(value);
        }

        vector.append("]");

        return vector.toString();
    }
}
