
package com.documentanalytics.backend.controller;

import com.documentanalytics.backend.dto.RegisterRequest;
import com.documentanalytics.backend.dto.RegisterResponse;
import com.documentanalytics.backend.service.RegistrationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(
            RegistrationService registrationService
    ) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response =
                registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
