package com.das.identity.application;

import com.das.identity.application.config.SecurityConfig;
import com.das.identity.domain.exceptions.AuthenticationDomainException;
import com.das.identity.domain.usecases.IdentityUseCaseFactory;
import com.das.identity.domain.usecases.LoginUseCase;
import com.das.identity.domain.usecases.dtos.LoginOutputDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Application-layer coverage for the OWASP controls the Identity pipeline claims:
 * A01 (filter chain lock-down), A03 (input validation), A05 (security headers),
 * A07 (generic auth failures).
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, AuthControllerTest.StubbedUseCases.class})
class AuthControllerTest {

    private static final String VALID_BODY = "{\"username\":\"alice\",\"password\":\"Apatehia65$\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private IdentityUseCaseFactory useCaseFactory;
    @Autowired private LoginUseCase loginUseCase;

    @BeforeEach
    void resetStubs() {
        reset(loginUseCase);
        when(useCaseFactory.getLoginUseCase()).thenReturn(loginUseCase);
    }

    @Nested
    @DisplayName("Successful login")
    class HappyPath {

        @Test
        @DisplayName("should return 200 with the token, username and roles")
        void shouldReturnToken() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("signed.jwt.token", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
        }

        @Test
        @DisplayName("should be reachable without authentication — it is the token entry point")
        void shouldBePubliclyReachable() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("t", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should never echo the submitted password back to the client")
        void shouldNotEchoPassword() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("t", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsStringIgnoringCase("Apatehia65$"))));
        }
    }

    @Nested
    @DisplayName("Failed login (OWASP A07)")
    class FailurePaths {

        @Test
        @DisplayName("should return 401 when the domain rejects the credentials")
        void shouldReturn401() throws Exception {
            when(loginUseCase.execute(any()))
                    .thenThrow(new AuthenticationDomainException("Invalid credentials"));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }

        @Test
        @DisplayName("should not return a token field on failure")
        void shouldNotReturnTokenOnFailure() throws Exception {
            when(loginUseCase.execute(any()))
                    .thenThrow(new AuthenticationDomainException("Invalid credentials"));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @Test
        @DisplayName("should return the same body for an unknown user and a wrong password")
        void shouldReturnIdenticalBodyForEveryFailure() throws Exception {
            when(loginUseCase.execute(any()))
                    .thenThrow(new AuthenticationDomainException("Invalid credentials"));

            String unknownUser = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            String wrongPassword = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertEquals(unknownUser, wrongPassword,
                    "differing responses would let an attacker enumerate valid usernames");
        }
    }

    @Nested
    @DisplayName("Input validation (OWASP A03)")
    class InputValidation {

        @Test
        @DisplayName("should reject a blank username with 400")
        void shouldRejectBlankUsername() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"\",\"password\":\"Apatehia65$\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject a blank password with 400")
        void shouldRejectBlankPassword() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject a username longer than 64 characters with 400")
        void shouldRejectOverlongUsername() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + "u".repeat(65) + "\",\"password\":\"pw\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject a malformed JSON body with 400")
        void shouldRejectMalformedJson() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not-json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should not reach the domain use case when validation fails")
        void shouldShortCircuitBeforeDomain() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());

            verify(loginUseCase, never()).execute(any());
        }
    }

    @Nested
    @DisplayName("Filter chain lock-down (OWASP A01)")
    class FilterChain {

        @Test
        @DisplayName("should deny an unmapped path — anyRequest() is denyAll()")
        void shouldDenyUnmappedPath() throws Exception {
            mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should reject GET on the login endpoint")
        void shouldRejectGetOnLogin() throws Exception {
            mockMvc.perform(get("/auth/login"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    @DisplayName("Security headers (OWASP A05)")
    class SecurityHeaders {

        @Test
        @DisplayName("should deny framing to block clickjacking")
        void shouldSendFrameOptionsDeny() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("t", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        @DisplayName("should send a restrictive Content-Security-Policy")
        void shouldSendCsp() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("t", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(header().string("Content-Security-Policy",
                            "default-src 'self'; frame-ancestors 'none'; object-src 'none'"));
        }

        @Test
        @DisplayName("should send Referrer-Policy and Permissions-Policy")
        void shouldSendReferrerAndPermissionsPolicy() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("t", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy",
                            "geolocation=(), microphone=(), camera=()"));
        }

        @Test
        @DisplayName("should send nosniff to block MIME-type confusion")
        void shouldSendNoSniff() throws Exception {
            when(loginUseCase.execute(any())).thenReturn(
                    new LoginOutputDTO("t", "alice", List.of("ROLE_USER")));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
    }

    // ── stubs ────────────────────────────────────────────────────────────────

    /**
     * {@link AuthController} resolves the use case per request via the factory, so both
     * the factory and the use case can be plain mocks re-stubbed in {@code @BeforeEach}.
     */
    @TestConfiguration
    static class StubbedUseCases {

        @Bean
        IdentityUseCaseFactory identityUseCaseFactory() {
            return mock(IdentityUseCaseFactory.class);
        }

        @Bean
        LoginUseCase loginUseCase() {
            return mock(LoginUseCase.class);
        }
    }
}
