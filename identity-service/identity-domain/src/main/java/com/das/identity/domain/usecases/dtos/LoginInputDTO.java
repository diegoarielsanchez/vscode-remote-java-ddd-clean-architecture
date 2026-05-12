package com.das.identity.domain.usecases.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input DTO for the Login use case.
 * Validated at the application boundary (OWASP A03 — input validation).
 */
public record LoginInputDTO(
    @NotBlank(message = "Username must not be blank")
    @Size(max = 64, message = "Username must not exceed 64 characters")
    String username,

    @NotBlank(message = "Password must not be blank")
    @Size(max = 128, message = "Password must not exceed 128 characters")
    String password
) {}
