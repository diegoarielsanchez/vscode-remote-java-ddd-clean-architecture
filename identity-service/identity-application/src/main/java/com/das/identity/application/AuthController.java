package com.das.identity.application;

import com.das.identity.domain.exceptions.AuthenticationDomainException;
import com.das.identity.domain.usecases.IdentityUseCaseFactory;
import com.das.identity.domain.usecases.dtos.LoginInputDTO;
import com.das.identity.domain.usecases.dtos.LoginOutputDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the single authentication entry point: POST /auth/login.
 * <p>
 * OWASP notes:
 * - A07: error responses never reveal whether username or password was wrong.
 * - A09: login attempts (success and failure) are written to the AUDIT logger.
 * - A03: input is validated via Jakarta Bean Validation before reaching the domain.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Issues JWT bearer tokens")
public class AuthController {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final IdentityUseCaseFactory useCaseFactory;

    public AuthController(IdentityUseCaseFactory useCaseFactory) {
        this.useCaseFactory = useCaseFactory;
    }

    /**
     * Authenticate a user and receive a JWT bearer token.
     *
     * @param input   {username, password}
     * @return        {token, username, roles}  on success
     *                {error}                   on failure (401)
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user and obtain a JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authentication successful — returns JWT token"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<?> login(@Valid @RequestBody LoginInputDTO input,
                                   HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String safeUsername = sanitize(input.username());

        try {
            LoginOutputDTO output = useCaseFactory.getLoginUseCase().execute(input);
            auditLog.info("AUTH_SUCCESS username={} ip={}", safeUsername, ip);
            return ResponseEntity.ok(output);

        } catch (AuthenticationDomainException ex) {
            // Generic message — do not reveal which field was wrong (OWASP A07)
            auditLog.warn("AUTH_FAILURE username={} ip={}", safeUsername, ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid credentials"));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return sanitize(forwarded.split(",")[0].trim());
        }
        return request.getRemoteAddr();
    }

    /** Strips log-injection characters (OWASP A09). */
    private String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[\r\n\t]", "_");
    }
}
