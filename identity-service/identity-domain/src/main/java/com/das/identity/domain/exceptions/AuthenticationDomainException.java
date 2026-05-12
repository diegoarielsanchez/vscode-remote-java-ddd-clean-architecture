package com.das.identity.domain.exceptions;

/**
 * Domain exception thrown when authentication fails.
 * Deliberately vague to prevent user enumeration (OWASP A07).
 */
public class AuthenticationDomainException extends RuntimeException {

    public AuthenticationDomainException(String message) {
        super(message);
    }
}
