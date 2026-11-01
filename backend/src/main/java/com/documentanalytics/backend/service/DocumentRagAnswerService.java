
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.repository.DocumentSemanticSearchRepository.SimilarChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentRagAnswerService {

    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int MAX_SOURCES = 3;

    // Initial relevance threshold; adjust after evaluating real queries.
    private static final double MIN_SIMILARITY = 0.20;

    private static final String NO_ANSWER =
            "I could not find that information in the document.";

    private final DocumentSemanticSearchService searchService;
    private final OllamaChatService chatService;

    public DocumentRagAnswerService(
            DocumentSemanticSearchService searchService,
            OllamaChatService chatService) {

        this.searchService = searchService;
        this.chatService = chatService;
    }

    public record RagSource(
            Long chunkId,
            Integer chunkIndex,
            String chunkText,
            double similarityScore
    ) {}

    public record RagAnswer(
            Long documentId,
            String question,
            String answer,
            String model,
            List<RagSource> sources
    ) {}

    public RagAnswer answerDocument(
            Long documentId,
            Long authenticatedUserId,
            String question) {

        // Step 1: Validate the user's question.
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Question must not be empty."
            );
        }

        String cleanQuestion = question.trim();

        if (cleanQuestion.length() > MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Question must contain at most 1000 characters."
            );
        }

        // Step 2: Search the document securely.
        // The search service verifies document ownership.
        List<SimilarChunk> searchResults =
                searchService.searchDocument(
                        documentId,
                        authenticatedUserId,
                        cleanQuestion,
                        MAX_SOURCES
                );

        // Step 3: Select relevant chunks and build context.
        List<RagSource> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();

        // Defensive filtering also protects against invalid/foreign repository metadata.
        var candidates = searchResults.stream()
                .filter(chunk -> chunk != null && documentId.equals(chunk.documentId())
                        && chunk.chunkId() != null && chunk.chunkId() > 0
                        && chunk.chunkIndex() != null && chunk.chunkIndex() >= 0
                        && Double.isFinite(chunk.similarityScore())
                        && chunk.similarityScore() >= MIN_SIMILARITY
                        && chunk.similarityScore() <= 1.0
                        && chunk.chunkText() != null && !chunk.chunkText().isBlank())
                .sorted(Comparator.comparingDouble(SimilarChunk::similarityScore).reversed()
                        .thenComparing(SimilarChunk::chunkIndex).thenComparing(SimilarChunk::chunkId))
                .toList();
        var selectedIds = new HashSet<Long>();
        int contextBudget = OllamaChatService.contextBudgetBytes(cleanQuestion);
        for (SimilarChunk chunk : candidates) {
            if (sources.size() == MAX_SOURCES) break;
            if (selectedIds.contains(chunk.chunkId())) continue;

            if (!Double.isFinite(chunk.similarityScore())
                    || chunk.similarityScore() < MIN_SIMILARITY) {
                continue;
            }

            String text = chunk.chunkText();

            if (text == null || text.isBlank()) {
                continue;
            }

            String sourceText = """
                    [Source %d | Chunk %d]
                    %s

                    """.formatted(
                            sources.size() + 1,
                            chunk.chunkIndex(),
                            text
                    );

            if ((context.toString() + sourceText).getBytes(StandardCharsets.UTF_8).length
                    > contextBudget) {
                // Preserve complete original chunks; a smaller later chunk may still fit.
                continue;
            }

            context.append(sourceText);
            selectedIds.add(chunk.chunkId());

            sources.add(new RagSource(
                    chunk.chunkId(),
                    chunk.chunkIndex(),
                    text,
                    chunk.similarityScore()
            ));
        }

        // Step 4: Avoid AI generation for weak matches.
        if (sources.isEmpty()) {
            return new RagAnswer(
                    documentId,
                    cleanQuestion,
                    NO_ANSWER,
                    chatService.getModelName(),
                    List.of()
            );
        }

        // Step 5: Generate a natural-language answer
        // using only the retrieved document context.
        String answer = chatService.generateAnswer(
                cleanQuestion,
                context.toString()
        );

        // Step 6: Return the answer with source references.
        return new RagAnswer(
                documentId,
                cleanQuestion,
                answer,
                chatService.getModelName(),
                List.copyOf(sources)
        );
    }
}
