package com.documentanalytics.backend;

import com.documentanalytics.backend.controller.DocumentRagAnswerController;
import com.documentanalytics.backend.service.DocumentRagAnswerService;
import com.documentanalytics.backend.exception.DocumentRagExceptionHandler;
import com.documentanalytics.backend.service.DocumentRagAnswerService.RagAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentRagAnswerControllerTest {
    private DocumentRagAnswerService service;
    private MockMvc mvc;
    private Jwt principal;

    @BeforeEach
    void setUp() {
        service = mock(DocumentRagAnswerService.class);
        principal = jwt("7");
        mvc = MockMvcBuilders.standaloneSetup(new DocumentRagAnswerController(service))
                .setControllerAdvice(new DocumentRagExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == Jwt.class;
                    }
                    public Object resolveArgument(MethodParameter parameter,
                            ModelAndViewContainer container, NativeWebRequest request,
                            WebDataBinderFactory binderFactory) {
                        return principal;
                    }
                }).build();
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("test-only-placeholder")
                .header("alg", "HS256").subject(subject).build();
    }

    @Test
    void registersPostEndpointAndUsesJwtIdentityInsteadOfBodyIdentity() throws Exception {
        String question = "How do patients book appointments?";
        when(service.answerDocument(42L, 7L, question)).thenReturn(
                new RagAnswer(42L, question, "Call reception.", "qwen2.5:0.5b", List.of()));
        mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How do patients book appointments?\",\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(DocumentRagAnswerController.class))
                .andExpect(jsonPath("$.documentId").value(42))
                .andExpect(jsonPath("$.answer").value("Call reception."))
                .andExpect(jsonPath("$.sources").isArray());
        verify(service).answerDocument(42L, 7L, question);
    }

    @Test
    void rejectsMissingAuthentication() throws Exception {
        principal = null;
        mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hello?\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidJwtSubjects() throws Exception {
        for (String subject : List.of("invalid", "0", "-7")) {
            principal = jwt(subject);
            mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"Hello?\"}")).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidDocumentIdAndMissingOrMalformedBody() throws Exception {
        mvc.perform(post("/api/documents/0/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hello?\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents/42/ask")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void preservesServiceOwnershipAndReadinessErrors() throws Exception {
        for (HttpStatus status : List.of(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)) {
            doThrow(new ResponseStatusException(status)).when(service)
                    .answerDocument(42L, 7L, "Hello?");
            mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"Hello?\"}")).andExpect(status().is(status.value()));
        }
    }

    @Test
    void sanitizesInternalErrorsAndPreservesTimeoutStatus() throws Exception {
        doThrow(new IllegalStateException("private database and upstream details")).when(service)
                .answerDocument(42L, 7L, "Hello?");
        mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hello?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The AI service is unavailable. Please try again shortly."))
                .andExpect(jsonPath("$.trace").doesNotExist());
        doThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "private details")).when(service)
                .answerDocument(42L, 7L, "Hello?");
        mvc.perform(post("/api/documents/42/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hello?\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message").value("AI answer generation timed out. Please try again."));
    }
}
