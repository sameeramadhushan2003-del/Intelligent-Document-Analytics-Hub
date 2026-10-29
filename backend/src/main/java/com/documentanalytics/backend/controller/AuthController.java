
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.dto.LoginRequest;
import com.documentanalytics.backend.dto.LoginResponse;
import com.documentanalytics.backend.dto.MeResponse;
import com.documentanalytics.backend.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse currentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return authService.currentUser(userId);
    }
}
