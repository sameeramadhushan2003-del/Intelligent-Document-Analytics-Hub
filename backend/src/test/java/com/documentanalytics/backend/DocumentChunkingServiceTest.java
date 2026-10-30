
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.DocumentChunk;
import com.documentanalytics.backend.service.DocumentChunkingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkingServiceTest {

    private DocumentChunkingService chunkingService;

    private static final Long DOCUMENT_ID = 34L;

    @BeforeEach
    void setUp() {
        chunkingService = new DocumentChunkingService();
    }

    // TEST 1: A short document produces one chunk.
    @Test
    void shortDocumentProducesOneChunk() {

        String text =
                "Intelligent Document Analytics Hub supports RAG.";

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                );

        assertEquals(1, chunks.size());

        DocumentChunk chunk = chunks.get(0);

        assertEquals(DOCUMENT_ID, chunk.getDocumentId());
        assertEquals(0, chunk.getChunkIndex());
        assertEquals(text, chunk.getChunkText());
        assertEquals(0, chunk.getStartOffset());
        assertEquals(text.length(), chunk.getEndOffset());
    }

    // TEST 2: A 2500-character document produces 3 chunks.
    @Test
    void longDocumentProducesMultipleChunks() {

        String text = "A".repeat(2500);

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                );

        assertEquals(3, chunks.size());

        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(1, chunks.get(1).getChunkIndex());
        assertEquals(2, chunks.get(2).getChunkIndex());

        assertEquals(0, chunks.get(0).getStartOffset());
        assertEquals(1000, chunks.get(0).getEndOffset());

        assertEquals(850, chunks.get(1).getStartOffset());
        assertEquals(1850, chunks.get(1).getEndOffset());

        assertEquals(1700, chunks.get(2).getStartOffset());
        assertEquals(2500, chunks.get(2).getEndOffset());
    }

    // TEST 3: Adjacent chunks share 150 characters.
    @Test
    void chunksHaveCorrectOverlap() {

        String text = "0123456789".repeat(110);

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                );

        assertEquals(2, chunks.size());

        DocumentChunk first = chunks.get(0);
        DocumentChunk second = chunks.get(1);

        String firstEnd = first.getChunkText()
                .substring(850);

        String secondStart = second.getChunkText()
                .substring(0, 150);

        assertEquals(firstEnd, secondStart);
    }

    // TEST 4: Chunk text matches the original offsets.
    @Test
    void chunkOffsetsMatchOriginalText() {

        String text = "ABCDE".repeat(500);

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                );

        for (DocumentChunk chunk : chunks) {

            String originalSection = text.substring(
                    chunk.getStartOffset(),
                    chunk.getEndOffset()
            );

            assertEquals(
                    originalSection,
                    chunk.getChunkText()
            );

            assertTrue(
                    chunk.getChunkText().length() <= 1000
            );
        }
    }

    // TEST 5: Exactly 1000 characters = one chunk.
    @Test
    void exactChunkSizeProducesOneChunk() {

        String text = "X".repeat(1000);

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                );

        assertEquals(1, chunks.size());
        assertEquals(1000, chunks.get(0).getEndOffset());
    }

    // TEST 6: Do not split a Unicode emoji in half.
    @Test
    void preservesEmojiAtChunkBoundary() {

        String text =
                "a".repeat(999)
                + "😀"
                + "b".repeat(400);

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                );

        assertEquals(2, chunks.size());

        assertEquals(
                999,
                chunks.get(0).getEndOffset()
        );

        assertTrue(
                chunks.get(1).getChunkText()
                        .contains("😀")
        );

        for (DocumentChunk chunk : chunks) {

            String content = chunk.getChunkText();

            assertFalse(
                    Character.isLowSurrogate(
                            content.charAt(0)
                    )
            );

            assertFalse(
                    Character.isHighSurrogate(
                            content.charAt(
                                    content.length() - 1
                            )
                    )
            );
        }
    }

    // TEST 7: Reject empty or whitespace-only content.
    @Test
    void rejectsEmptyText() {

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        ""
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        "    "
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        null
                )
        );
    }

    // TEST 8: Reject invalid document IDs.
    @Test
    void rejectsInvalidDocumentId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkingService.chunkDocument(
                        null,
                        "Some document text"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkingService.chunkDocument(
                        -1L,
                        "Some document text"
                )
        );
    }

    // TEST 9: Reject extracted text larger than 2M chars.
    @Test
    void rejectsOversizedText() {

        String text = "A".repeat(2_000_001);

        assertThrows(
                IllegalArgumentException.class,
                () -> chunkingService.chunkDocument(
                        DOCUMENT_ID,
                        text
                )
        );
    }
}
