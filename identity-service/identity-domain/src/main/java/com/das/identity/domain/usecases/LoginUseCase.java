package com.das.identity.domain.usecases;

import com.das.identity.domain.usecases.dtos.LoginInputDTO;
import com.das.identity.domain.usecases.dtos.LoginOutputDTO;

/**
 * Use case port (primary/driving port): authenticate a user and issue a JWT.
 */
public interface LoginUseCase {

    LoginOutputDTO execute(LoginInputDTO input);
}
