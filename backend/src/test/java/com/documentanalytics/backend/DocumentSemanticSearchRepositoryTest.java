package com.documentanalytics.backend;

import com.documentanalytics.backend.repository.DocumentSemanticSearchRepository;
import com.documentanalytics.backend.repository.DocumentSemanticSearchRepository.SimilarChunk;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentSemanticSearchRepositoryTest {
    @Test
    void convertsCosineDistanceAndNeverTreatsNullDistanceAsPerfectMatch() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var rs = mock(ResultSet.class);
        when(rs.getLong("chunk_id")).thenReturn(1L);
        when(rs.getLong("document_id")).thenReturn(42L);
        when(rs.getDouble("cosine_distance")).thenReturn(0.25);
        when(rs.wasNull()).thenReturn(false, true);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<SimilarChunk>>any(),
                any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertTrue(sql.contains("c.document_id = ?"));
            assertTrue(sql.contains("cosine_distance ASC NULLS LAST"));
            assertTrue(sql.contains("c.chunk_index ASC, c.id ASC"));
            RowMapper<SimilarChunk> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0), mapper.mapRow(rs, 1));
        });
        var vector = new float[384];
        vector[0] = 1;
        var results = new DocumentSemanticSearchRepository(jdbc).findSimilarChunks(42L, "all-minilm", vector, 3);
        assertEquals(0.75, results.getFirst().similarityScore());
        assertTrue(Double.isNaN(results.get(1).similarityScore()));
    }
}
