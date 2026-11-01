package com.documentanalytics.backend;

import com.documentanalytics.backend.dto.*;
import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.repository.AppUserRepository;
import com.documentanalytics.backend.service.*;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthInputValidationTest {
    @Test
    void validatesRegistrationAndLoginWithoutDatabaseOrRealCredentials() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new RegisterRequest("Synthetic Student", "student@example.invalid", "synthetic-fixture")).isEmpty());
            assertFalse(validator.validate(new RegisterRequest("", "invalid", "short")).isEmpty());
            assertFalse(validator.validate(new LoginRequest("invalid", " ")).isEmpty());
            assertFalse(validator.validate(new LoginRequest("a".repeat(321), "synthetic-fixture")).isEmpty());
            assertFalse(validator.validate(new LoginRequest("student@example.invalid", "x".repeat(73))).isEmpty());
        }
    }

    @Test
    void rejectsMultibyteBcryptOverflowBeforeRepositoryLookup() {
        var users = mock(AppUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var tokens = mock(TokenService.class);
        var service = new AuthService(users, encoder, tokens);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.login(new LoginRequest("student@example.invalid", "\u6587".repeat(25))))
                .getStatusCode().value());
        verifyNoInteractions(users, encoder, tokens);
    }

    @Test
    void invalidPasswordAndInactiveUsersNeverReceiveTokens() {
        var users = mock(AppUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var tokens = mock(TokenService.class);
        var account = mock(AppUser.class);
        when(users.findByEmailIgnoreCase("student@example.invalid")).thenReturn(Optional.of(account));
        var service = new AuthService(users, encoder, tokens);
        var request = new LoginRequest("student@example.invalid", "synthetic-fixture");
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.login(request)).getStatusCode().value());
        when(account.isActive()).thenReturn(true);
        when(account.getPasswordHash()).thenReturn("synthetic-hash");
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.login(request)).getStatusCode().value());
        verifyNoInteractions(tokens);
    }
}
