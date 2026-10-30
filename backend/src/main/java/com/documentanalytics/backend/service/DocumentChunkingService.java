
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.entity.DocumentChunk;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkingService {

    // Maximum number of Java characters in each chunk.
    public static final int CHUNK_SIZE = 1000;

    // Number of characters shared between adjacent chunks.
    public static final int CHUNK_OVERLAP = 150;

    // Match the extracted-text size limit.
    private static final int MAX_TEXT_CHARACTERS = 2_000_000;

    public List<DocumentChunk> chunkDocument(
            Long documentId,
            String extractedText
    ) {

        // Validate the document ID.
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException(
                    "A valid document ID is required."
            );
        }

        // Validate the extracted text.
        if (extractedText == null
                || extractedText.isBlank()) {

            throw new IllegalArgumentException(
                    "Extracted text cannot be empty."
            );
        }

        if (extractedText.length() > MAX_TEXT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Extracted text exceeds the maximum limit."
            );
        }

        List<DocumentChunk> chunks = new ArrayList<>();

        int textLength = extractedText.length();
        int start = 0;

        while (start < textLength) {

            // Calculate the end of the current chunk.
            int end = Math.min(
                    start + CHUNK_SIZE,
                    textLength
            );

            // Avoid splitting a Unicode surrogate pair.
            if (end < textLength
                    && Character.isHighSurrogate(
                            extractedText.charAt(end - 1)
                    )
                    && Character.isLowSurrogate(
                            extractedText.charAt(end)
                    )) {
                end--;
            }

            // Preserve the exact text and its offsets.
            String chunkText =
                    extractedText.substring(start, end);

            // Do not create whitespace-only chunks.
            if (!chunkText.isBlank()) {

                DocumentChunk chunk = new DocumentChunk(
                        documentId,
                        chunks.size(),
                        chunkText,
                        start,
                        end
                );

                chunks.add(chunk);
            }

            // Stop after processing the final characters.
            if (end == textLength) {
                break;
            }

            // Move forward while preserving overlap.
            int nextStart = end - CHUNK_OVERLAP;

            // Avoid starting in the middle of an emoji
            // or other supplementary Unicode character.
            if (nextStart > 0
                    && Character.isHighSurrogate(
                            extractedText.charAt(nextStart - 1)
                    )
                    && Character.isLowSurrogate(
                            extractedText.charAt(nextStart)
                    )) {
                nextStart--;
            }

            start = nextStart;
        }

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "No usable chunks could be generated."
            );
        }

        return List.copyOf(chunks);
    }
}
