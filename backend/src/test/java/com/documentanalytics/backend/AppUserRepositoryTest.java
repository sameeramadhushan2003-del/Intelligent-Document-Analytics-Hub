
package com.documentanalytics.backend;

import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.entity.UserRole;
import com.documentanalytics.backend.repository.AppUserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldSaveAndFindUserByEmail() {

        String email =
                "test-" + UUID.randomUUID() + "@example.test";

        AppUser user = new AppUser(
                "Test Student",
                email,
                "TEST_ONLY_NOT_A_REAL_PASSWORD_HASH"
        );

        AppUser savedUser =
                appUserRepository.saveAndFlush(user);

        assertNotNull(savedUser.getId());

        entityManager.clear();

        AppUser retrievedUser = appUserRepository
                .findByEmailIgnoreCase(
                        email.toUpperCase(Locale.ROOT)
                )
                .orElseThrow();

        assertEquals(
                "Test Student",
                retrievedUser.getFullName()
        );

        assertEquals(
                UserRole.USER,
                retrievedUser.getRole()
        );

        assertTrue(retrievedUser.isActive());

        assertNotNull(retrievedUser.getCreatedAt());

        assertTrue(
                appUserRepository.existsByEmailIgnoreCase(email)
        );
    }
}
