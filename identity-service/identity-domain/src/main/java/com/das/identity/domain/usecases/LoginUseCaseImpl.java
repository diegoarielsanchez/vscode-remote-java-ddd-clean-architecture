package com.das.identity.domain.usecases;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.exceptions.AuthenticationDomainException;
import com.das.identity.domain.ports.PasswordEncoderPort;
import com.das.identity.domain.ports.TokenPort;
import com.das.identity.domain.ports.UserRepository;
import com.das.identity.domain.usecases.dtos.LoginInputDTO;
import com.das.identity.domain.usecases.dtos.LoginOutputDTO;

import java.util.stream.Collectors;

/**
 * Domain use-case: authenticate a user and return a signed JWT.
 * <p>
 * OWASP notes:
 * - A07: error message is generic — does not reveal whether username or password was wrong.
 * - A02: password is never logged or returned; BCrypt comparison via port.
 * - A04: inactive accounts are rejected after credential check (timing-safe: always hash-compare first).
 */
public class LoginUseCaseImpl implements LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenPort tokenPort;

    public LoginUseCaseImpl(UserRepository userRepository,
                            PasswordEncoderPort passwordEncoder,
                            TokenPort tokenPort) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPort       = tokenPort;
    }

    @Override
    public LoginOutputDTO execute(LoginInputDTO input) {
        User user = userRepository.findByUsername(input.username())
                .orElse(null);

        // Always perform a hash comparison to prevent timing-based user enumeration
        String candidateHash = (user != null) ? user.getPasswordHash() : "$2a$12$invalidhashpadding000000000000000000000000000000000000000";
        boolean passwordMatches = passwordEncoder.matches(input.password(), candidateHash);

        if (user == null || !passwordMatches) {
            throw new AuthenticationDomainException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new AuthenticationDomainException("Invalid credentials");
        }

        String token = tokenPort.generateToken(user);

        return new LoginOutputDTO(
            token,
            user.getUsername(),
            user.getRoles().stream().map(role -> role.name()).collect(Collectors.toList())
        );
    }
}
