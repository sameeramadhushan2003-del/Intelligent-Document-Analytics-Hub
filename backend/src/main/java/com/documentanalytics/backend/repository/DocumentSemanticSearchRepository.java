
package com.documentanalytics.backend.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentSemanticSearchRepository {

    private static final int EXPECTED_DIMENSIONS = 384;
    private static final int MAX_RESULTS = 20;

    private final JdbcTemplate jdbcTemplate;

    public DocumentSemanticSearchRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SimilarChunk(
            Long chunkId,
            Long documentId,
            Integer chunkIndex,
            String chunkText,
            double similarityScore
    ) {}

    public List<SimilarChunk> findSimilarChunks(
            Long documentId,
            String modelName,
            float[] queryEmbedding,
            int limit) {

        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid document ID."
            );
        }

        if (modelName == null || modelName.isBlank()
                || modelName.length() > 120) {
            throw new IllegalArgumentException(
                    "Invalid embedding model name."
            );
        }

        if (limit < 1 || limit > MAX_RESULTS) {
            throw new IllegalArgumentException(
                    "Result limit must be between 1 and 20."
            );
        }

        String vectorValue = toVectorString(queryEmbedding);

        String sql = """
                SELECT
                    c.id AS chunk_id,
                    c.document_id,
                    c.chunk_index,
                    c.chunk_text,
                    e.embedding <=> CAST(? AS vector)
                        AS cosine_distance
                FROM document_chunk_embeddings e
                JOIN document_chunks c
                    ON c.id = e.chunk_id
                WHERE c.document_id = ?
                  AND e.embedding_model = ?
                  AND e.embedding_dimensions = ?
                ORDER BY cosine_distance ASC NULLS LAST,
                         c.chunk_index ASC
                LIMIT ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new SimilarChunk(
                        rs.getLong("chunk_id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("chunk_text"),
                        1.0 - rs.getDouble("cosine_distance")
                ),
                vectorValue,
                documentId,
                modelName.trim(),
                EXPECTED_DIMENSIONS,
                limit
        );
    }

    private String toVectorString(float[] embedding) {

        if (embedding == null ||
                embedding.length != EXPECTED_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Query embedding must have 384 dimensions."
            );
        }

        StringBuilder vector = new StringBuilder("[");
        double squaredNorm = 0;

        for (int i = 0; i < embedding.length; i++) {

            float value = embedding[i];

            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Query embedding contains an invalid value."
                );
            }

            squaredNorm += (double) value * value;

            if (i > 0) {
                vector.append(",");
            }

            vector.append(value);
        }

        if (squaredNorm == 0) {
            throw new IllegalArgumentException(
                    "Query embedding cannot be a zero vector."
            );
        }

        vector.append("]");
        return vector.toString();
    }
}
