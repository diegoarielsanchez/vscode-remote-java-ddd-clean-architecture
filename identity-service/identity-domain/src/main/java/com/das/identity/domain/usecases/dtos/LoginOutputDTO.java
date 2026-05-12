package com.das.identity.domain.usecases.dtos;

import java.util.List;

/**
 * Output DTO returned by the Login use case.
 * The token is a signed JWT — clients must include it as Bearer in Authorization header.
 */
public record LoginOutputDTO(
    String token,
    String username,
    List<String> roles
) {}
