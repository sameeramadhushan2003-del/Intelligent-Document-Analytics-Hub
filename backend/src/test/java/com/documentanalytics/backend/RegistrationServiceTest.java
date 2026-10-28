
package com.documentanalytics.backend;

import com.documentanalytics.backend.dto.RegisterRequest;
import com.documentanalytics.backend.dto.RegisterResponse;
import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.entity.UserRole;
import com.documentanalytics.backend.exception.DuplicateEmailException;
import com.documentanalytics.backend.repository.AppUserRepository;
import com.documentanalytics.backend.service.RegistrationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RegistrationServiceTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldRegisterUserWithHashedPassword() {

        String email =
                "registration-" + UUID.randomUUID() + "@example.test";

        RegisterRequest request = new RegisterRequest(
                "Test Student",
                email,
                "StrongTestPassword123!"
        );

        RegisterResponse response =
                registrationService.register(request);

        assertNotNull(response.id());
        assertEquals("USER", response.role());

        AppUser savedUser = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow();

        assertEquals(UserRole.USER, savedUser.getRole());

        assertNotEquals(
                request.password(),
                savedUser.getPasswordHash()
        );

        assertTrue(passwordEncoder.matches(
                request.password(),
                savedUser.getPasswordHash()
        ));
    }

    @Test
    void shouldRejectDuplicateEmail() {

        String email =
                "duplicate-" + UUID.randomUUID() + "@example.test";

        RegisterRequest request = new RegisterRequest(
                "Test Student",
                email,
                "StrongTestPassword123!"
        );

        registrationService.register(request);

        assertThrows(
                DuplicateEmailException.class,
                () -> registrationService.register(request)
        );
    }
}
