package com.documentanalytics.backend;

import com.documentanalytics.backend.repository.DocumentChunkEmbeddingRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentChunkEmbeddingRepositoryTest {

    @Test
    void persistsA384DimensionVectorUsingConflictSafeInsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DocumentChunkEmbeddingRepository repository =
                new DocumentChunkEmbeddingRepository(jdbc);
        float[] vector = new float[384];
        vector[0] = 0.125f;
        vector[383] = -0.5f;

        repository.saveOrUpdateEmbedding(8L, "all-minilm", vector);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("ON CONFLICT (chunk_id, embedding_model)"));
        assertTrue(sql.getValue().contains("DO NOTHING"));
        assertEquals(8L, arguments.getValue()[0]);
        assertEquals("all-minilm", arguments.getValue()[1]);
        assertEquals(384, arguments.getValue()[2]);
        String vectorValue = (String) arguments.getValue()[3];
        assertTrue(vectorValue.startsWith("["));
        assertTrue(vectorValue.endsWith("]"));
        assertEquals(384, vectorValue.substring(1, vectorValue.length() - 1).split(",").length);
        assertTrue(Arrays.stream(vectorValue.substring(1, vectorValue.length() - 1).split(","))
                .anyMatch("0.125"::equals));
    }

    @Test
    void rejectsWrongDimensionsBeforeDatabaseWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DocumentChunkEmbeddingRepository repository =
                new DocumentChunkEmbeddingRepository(jdbc);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.saveOrUpdateEmbedding(8L, "all-minilm", new float[383])
        );
        verifyNoInteractions(jdbc);
    }

    @Test
    void propagatesDatabasePersistenceFailure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "synthetic persistence failure"
                ));
        DocumentChunkEmbeddingRepository repository =
                new DocumentChunkEmbeddingRepository(jdbc);

        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> repository.saveOrUpdateEmbedding(8L, "all-minilm", new float[384])
        );
    }
}
