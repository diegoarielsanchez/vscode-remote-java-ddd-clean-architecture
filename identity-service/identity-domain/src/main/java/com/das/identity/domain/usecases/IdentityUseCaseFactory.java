package com.das.identity.domain.usecases;

import com.das.identity.domain.ports.PasswordEncoderPort;
import com.das.identity.domain.ports.TokenPort;
import com.das.identity.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Factory that wires domain use-cases to their port implementations.
 * Controllers depend on this factory rather than instantiating use-cases directly.
 */
@Service
public class IdentityUseCaseFactory {

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenPort tokenPort;

    public IdentityUseCaseFactory(UserRepository userRepository,
                                  PasswordEncoderPort passwordEncoder,
                                  TokenPort tokenPort) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPort       = tokenPort;
    }

    public LoginUseCase getLoginUseCase() {
        return new LoginUseCaseImpl(userRepository, passwordEncoder, tokenPort);
    }
}
