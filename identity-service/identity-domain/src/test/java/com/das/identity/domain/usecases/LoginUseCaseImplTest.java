package com.das.identity.domain.usecases;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserId;
import com.das.identity.domain.entities.UserRole;
import com.das.identity.domain.exceptions.AuthenticationDomainException;
import com.das.identity.domain.ports.PasswordEncoderPort;
import com.das.identity.domain.ports.TokenPort;
import com.das.identity.domain.ports.UserRepository;
import com.das.identity.domain.usecases.dtos.LoginInputDTO;
import com.das.identity.domain.usecases.dtos.LoginOutputDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the OWASP A07 (Identification &amp; Authentication Failures) controls that the
 * Identity Service's Jenkins pipeline claims to enforce.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginUseCase")
class LoginUseCaseImplTest {

    private static final String STORED_HASH = "$2a$12$storedhashforalice000000";

    @Mock private UserRepository      userRepository;
    @Mock private PasswordEncoderPort passwordEncoder;
    @Mock private TokenPort           tokenPort;

    private LoginUseCaseImpl useCase;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCaseImpl(userRepository, passwordEncoder, tokenPort);

        activeUser = User.reconstitute(new UserId("user-1"), "alice", STORED_HASH,
                List.of(UserRole.ROLE_USER), true);
        inactiveUser = User.reconstitute(new UserId("user-2"), "bob", STORED_HASH,
                List.of(UserRole.ROLE_USER), false);
    }

    @Nested
    @DisplayName("Successful authentication")
    class HappyPath {

        @Test
        @DisplayName("should return a signed token with the username and roles")
        void shouldIssueToken() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("correct-password", STORED_HASH)).thenReturn(true);
            when(tokenPort.generateToken(activeUser)).thenReturn("signed.jwt.token");

            LoginOutputDTO out = useCase.execute(new LoginInputDTO("alice", "correct-password"));

            assertEquals("signed.jwt.token", out.token());
            assertEquals("alice", out.username());
            assertEquals(List.of("ROLE_USER"), out.roles());
        }

        @Test
        @DisplayName("should generate the token for the authenticated user only")
        void shouldGenerateTokenForAuthenticatedUser() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(any(), anyString())).thenReturn(true);
            when(tokenPort.generateToken(any())).thenReturn("signed.jwt.token");

            useCase.execute(new LoginInputDTO("alice", "correct-password"));

            verify(tokenPort, times(1)).generateToken(activeUser);
        }

        @Test
        @DisplayName("should map every granted role onto the output DTO")
        void shouldMapAllRoles() {
            User admin = User.reconstitute(new UserId("user-3"), "root", STORED_HASH,
                    List.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN), true);
            when(userRepository.findByUsername("root")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches(any(), anyString())).thenReturn(true);
            when(tokenPort.generateToken(any())).thenReturn("t");

            LoginOutputDTO out = useCase.execute(new LoginInputDTO("root", "pw"));

            assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), out.roles());
        }
    }

    @Nested
    @DisplayName("Failed authentication (OWASP A07)")
    class FailurePaths {

        @Test
        @DisplayName("should reject an unknown username")
        void shouldRejectUnknownUser() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            when(passwordEncoder.matches(any(), anyString())).thenReturn(false);

            assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("ghost", "whatever")));
        }

        @Test
        @DisplayName("should reject a wrong password")
        void shouldRejectWrongPassword() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrong-password", STORED_HASH)).thenReturn(false);

            assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("alice", "wrong-password")));
        }

        @Test
        @DisplayName("should reject a deactivated account even when the password is correct")
        void shouldRejectInactiveAccount() {
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(inactiveUser));
            when(passwordEncoder.matches("correct-password", STORED_HASH)).thenReturn(true);

            assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("bob", "correct-password")));
        }

        @Test
        @DisplayName("should never issue a token when authentication fails")
        void shouldNotIssueTokenOnFailure() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(any(), anyString())).thenReturn(false);

            assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("alice", "wrong-password")));

            verify(tokenPort, never()).generateToken(any());
        }
    }

    @Nested
    @DisplayName("User enumeration resistance (OWASP A07)")
    class EnumerationResistance {

        @Test
        @DisplayName("should return an identical message for unknown user, wrong password and inactive account")
        void shouldUseIdenticalMessageForEveryFailure() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(inactiveUser));
            when(passwordEncoder.matches(eq("wrong"), anyString())).thenReturn(false);
            when(passwordEncoder.matches(eq("correct-password"), anyString())).thenReturn(true);

            String unknownUser = assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("ghost", "wrong"))).getMessage();
            String wrongPassword = assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("alice", "wrong"))).getMessage();
            String inactive = assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("bob", "correct-password"))).getMessage();

            assertEquals(unknownUser, wrongPassword,
                    "a different message would let an attacker enumerate valid usernames");
            assertEquals(wrongPassword, inactive,
                    "a different message would reveal that the account exists but is disabled");
        }

        @Test
        @DisplayName("failure messages should not echo the submitted username or password")
        void shouldNotLeakCredentialsInMessage() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches(any(), anyString())).thenReturn(false);

            String message = assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("alice", "hunter2"))).getMessage();

            assertFalse(message.contains("alice"));
            assertFalse(message.contains("hunter2"));
        }

        @Test
        @DisplayName("should still run a hash comparison when the user does not exist (timing-safe)")
        void shouldHashCompareEvenForUnknownUser() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            when(passwordEncoder.matches(any(), anyString())).thenReturn(false);

            assertThrows(AuthenticationDomainException.class,
                    () -> useCase.execute(new LoginInputDTO("ghost", "whatever")));

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(passwordEncoder, times(1)).matches(any(), hashCaptor.capture());
            assertTrue(hashCaptor.getValue().startsWith("$2a$"),
                    "a BCrypt-shaped dummy hash must be compared so the response time does not "
                            + "reveal whether the username exists");
        }
    }
}
