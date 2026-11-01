package com.documentanalytics.backend;

import com.documentanalytics.backend.config.SecurityConfig;
import com.documentanalytics.backend.controller.*;
import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.entity.Document;
import com.documentanalytics.backend.entity.DocumentStatus;
import com.documentanalytics.backend.exception.DocumentEmbeddingExceptionHandler;
import com.documentanalytics.backend.exception.DocumentLibraryExceptionHandler;
import com.documentanalytics.backend.exception.DocumentRagExceptionHandler;
import com.documentanalytics.backend.repository.*;
import com.documentanalytics.backend.service.*;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security filter, encoder/decoder, controllers and ownership services; no database. */
class DocumentSecurityIntegrationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() throws Exception {
        var generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        String syntheticKey = Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("synthetic-only",
                Map.of("security.jwt.secret-base64", syntheticKey)));
        context.register(TestConfig.class);
        context.refresh();
        encoder = context.getBean(JwtEncoder.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        var active = mock(AppUser.class);
        when(active.isActive()).thenReturn(true);
        when(context.getBean(AppUserRepository.class).findById(7L)).thenReturn(Optional.of(active));
    }

    @AfterEach
    void close() { if (context != null) context.close(); }

    private String token(String subject, Instant expires, String issuer) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(Instant.now().minusSeconds(900)).expiresAt(expires).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private AbstractMockHttpServletRequestBuilder<?>[] protectedRequests() {
        return new AbstractMockHttpServletRequestBuilder<?>[] {
            get("/api/documents"), get("/api/documents/79"),
            post("/api/documents/79/process"), post("/api/documents/79/chunks/generate"),
            post("/api/documents/79/embeddings/generate"),
            post("/api/documents/79/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"How?\"}"),
            post("/api/documents/79/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"How?\",\"userId\":999}"),
            delete("/api/documents/79"),
            multipart("/api/documents/upload").file(new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{65})),
            patch("/api/documents/79").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated title\"}")
        };
    }

    private void noPrivateWork() {
        verifyNoInteractions(context.getBean(DocumentTextExtractionService.class),
                context.getBean(OllamaEmbeddingService.class), context.getBean(OllamaChatService.class),
                context.getBean(PrivateFileStorageService.class), context.getBean(DocumentChunkRepository.class),
                context.getBean(DocumentExtractionRepository.class), context.getBean(DocumentChunkEmbeddingRepository.class),
                context.getBean(DocumentSemanticSearchRepository.class));
    }

    @Test
    void allDocumentEndpointsRejectAbsentMalformedExpiredAndInvalidIdentityTokens() throws Exception {
        String[] invalid = {null, "malformed", token("7", Instant.now().minusSeconds(300), "document-analytics-hub"),
                token("not-a-user", Instant.now().plusSeconds(900), "document-analytics-hub"),
                token("0", Instant.now().plusSeconds(900), "document-analytics-hub"),
                token("7", Instant.now().plusSeconds(900), "wrong-issuer")};
        for (String bearer : invalid) {
            for (var request : protectedRequests()) {
                if (bearer != null) request.header("Authorization", "Bearer " + bearer);
                mvc.perform(request).andExpect(status().isUnauthorized());
            }
        }
        verifyNoInteractions(context.getBean(AppUserRepository.class), context.getBean(DocumentRepository.class));
        noPrivateWork();
    }

    @Test
    void validJwtCannotReadOrProcessAnotherUsersOrMissingDocument() throws Exception {
        String bearer = token("7", Instant.now().plusSeconds(900), "document-analytics-hub");
        var requests = protectedRequests();
        // Library and upload are covered separately; these seven operations target one document.
        for (int i = 1; i <= 7; i++) {
            mvc.perform(requests[i].header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isNotFound());
        }
        verify(context.getBean(DocumentRepository.class), times(3)).findByIdAndOwnerUserId(79L, 7L);
        verify(context.getBean(DocumentRepository.class), times(4)).findByIdAndOwnerUserIdForUpdate(79L, 7L);
        noPrivateWork();
    }

    @Test
    void inactiveAccountCannotTriggerInferenceOrStorage() throws Exception {
        var inactive = mock(AppUser.class);
        when(context.getBean(AppUserRepository.class).findById(7L)).thenReturn(Optional.of(inactive));
        String bearer = token("7", Instant.now().plusSeconds(900), "document-analytics-hub");
        for (var request : protectedRequests()) {
            mvc.perform(request.header("Authorization", "Bearer " + bearer)).andExpect(status().isForbidden());
        }
        noPrivateWork();
    }

    @Test
    void forgedSignatureAndMissingExpiryAreRejected() throws Exception {
        var claims = JwtClaimsSet.builder().issuer("document-analytics-hub").subject("7").build();
        String noExpiry = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + noExpiry))
                .andExpect(status().isUnauthorized());
        var other = KeyGenerator.getInstance("HmacSHA256");
        other.init(256);
        var foreignEncoder = new SecurityConfig().jwtEncoder(other.generateKey());
        String forged = foreignEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("document-analytics-hub").subject("7")
                        .expiresAt(Instant.now().plusSeconds(900)).build())).getTokenValue();
        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
        noPrivateWork();
    }

    @Test
    void ownerCanDeleteDocumentMetadataWithoutAccessingOtherOwners() throws Exception {
        Document document = mock(Document.class);
        when(document.getStorageKey()).thenReturn("synthetic-storage-key");
        when(context.getBean(DocumentRepository.class)
                .findByIdAndOwnerUserIdForUpdate(79L, 7L))
                .thenReturn(Optional.of(document));
        when(context.getBean(PrivateFileStorageService.class)
                .stageStoredFileDeletion("synthetic-storage-key"))
                .thenReturn(null);

        String bearer = token(
                "7",
                Instant.now().plusSeconds(900),
                "document-analytics-hub"
        );
        TransactionSynchronizationManager.initSynchronization();
        try {
            mvc.perform(delete("/api/documents/79")
                            .header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isNoContent());
            verify(context.getBean(DocumentRepository.class)).delete(document);
            verify(context.getBean(DocumentRepository.class)).flush();
            verify(context.getBean(PrivateFileStorageService.class))
                    .stageStoredFileDeletion("synthetic-storage-key");

            var synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                );
            }
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void ownerCanUpdateOnlyTheDocumentTitle() throws Exception {
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(79L);
        when(document.getTitle()).thenReturn("Updated title");
        when(document.getOriginalFilename()).thenReturn("original.pdf");
        when(document.getContentType()).thenReturn("application/pdf");
        when(document.getFileSizeBytes()).thenReturn(123L);
        when(document.getProcessingStatus()).thenReturn(DocumentStatus.UPLOADED);
        when(document.getUploadedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(context.getBean(DocumentRepository.class)
                .findByIdAndOwnerUserIdForUpdate(79L, 7L))
                .thenReturn(Optional.of(document));
        when(context.getBean(DocumentRepository.class).save(document))
                .thenReturn(document);

        String bearer = token(
                "7",
                Instant.now().plusSeconds(900),
                "document-analytics-hub"
        );
        mvc.perform(patch("/api/documents/79")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.originalFilename").value("original.pdf"))
                .andExpect(jsonPath("$.processingStatus").value("UPLOADED"));

        verify(document).setTitle("Updated title");
        verify(context.getBean(DocumentRepository.class)).save(document);
        verify(document, never()).setOriginalFilename(anyString());
        verify(document, never()).setContentType(anyString());
        verify(document, never()).setFileSizeBytes(anyLong());
        verify(document, never()).setProcessingStatus(any());
        noPrivateWork();
    }

    @Test
    void titleUpdateRejectsInvalidInputAndHidesMissingOrUnownedDocuments() throws Exception {
        String bearer = token(
                "7",
                Instant.now().plusSeconds(900),
                "document-analytics-hub"
        );
        var repository = context.getBean(DocumentRepository.class);
        when(repository.findByIdAndOwnerUserIdForUpdate(79L, 7L))
                .thenReturn(Optional.empty());

        for (String title : new String[]{"", "   ", "x".repeat(256)}) {
            mvc.perform(patch("/api/documents/79")
                            .header("Authorization", "Bearer " + bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + title + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists());
        }

        mvc.perform(patch("/api/documents/79")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Valid title\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());

        verify(repository, times(1)).findByIdAndOwnerUserIdForUpdate(79L, 7L);
        verify(repository, never()).save(any());
        noPrivateWork();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, DocumentLibraryController.class, DocumentUploadController.class,
            DocumentProcessingController.class, DocumentChunkController.class, DocumentEmbeddingController.class,
            DocumentSemanticSearchController.class, DocumentRagAnswerController.class,
            DocumentLibraryExceptionHandler.class, DocumentEmbeddingExceptionHandler.class,
            DocumentLibraryService.class, DocumentUploadService.class, DocumentProcessingService.class,
            DocumentChunkPersistenceService.class, DocumentEmbeddingPersistenceService.class,
            DocumentSemanticSearchService.class, DocumentRagAnswerService.class, DocumentDeletionService.class,
            DocumentRagExceptionHandler.class})
    static class TestConfig {
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean DocumentRepository documents() { return mock(DocumentRepository.class); }
        @Bean DocumentChunkRepository chunks() { return mock(DocumentChunkRepository.class); }
        @Bean DocumentExtractionRepository extractions() { return mock(DocumentExtractionRepository.class); }
        @Bean DocumentChunkEmbeddingRepository embeddings() { return mock(DocumentChunkEmbeddingRepository.class); }
        @Bean DocumentSemanticSearchRepository search() { return mock(DocumentSemanticSearchRepository.class); }
        @Bean DocumentTextExtractionService extractor() { return mock(DocumentTextExtractionService.class); }
        @Bean DocumentChunkingService chunker() { return mock(DocumentChunkingService.class); }
        @Bean OllamaEmbeddingService embeddingModel() { return mock(OllamaEmbeddingService.class); }
        @Bean OllamaChatService chatModel() { return mock(OllamaChatService.class); }
        @Bean PrivateFileStorageService storage() { return mock(PrivateFileStorageService.class); }
    }
}
