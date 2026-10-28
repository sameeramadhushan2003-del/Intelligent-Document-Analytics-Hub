
package com.documentanalytics.backend.service;

import com.documentanalytics.backend.dto.RegisterRequest;
import com.documentanalytics.backend.dto.RegisterResponse;
import com.documentanalytics.backend.entity.AppUser;
import com.documentanalytics.backend.exception.DuplicateEmailException;
import com.documentanalytics.backend.repository.AppUserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class RegistrationService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String fullName = request.fullName().trim();

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String password = request.password();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }

        String passwordHash = passwordEncoder.encode(password);

        AppUser user = new AppUser(
                fullName,
                email,
                passwordHash
        );

        AppUser savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                savedUser.getCreatedAt()
        );
    }
}
