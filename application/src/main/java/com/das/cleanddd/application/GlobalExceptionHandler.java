package com.das.cleanddd.application;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorMainResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorMainResponse> handleInvalidInputException(InvalidInputException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // OWASP: Handle validation errors securely (don't leak internal validation details)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // OWASP: Handle constraint violations
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        for (ConstraintViolation<?> violation : violations) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        }
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // OWASP: Handle authentication exceptions
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ErrorMainResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.UNAUTHORIZED.value(),
            "Authentication failed");
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // OWASP: Handle authorization exceptions
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorMainResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.FORBIDDEN.value(),
            "Access denied");
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // OWASP: Handle rate limiting (429 Too Many Requests)
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<ErrorMainResponse> handleRateLimitExceededException(RateLimitExceededException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.TOO_MANY_REQUESTS.value(),
            "Rate limit exceeded. Please try again later.");
        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorMainResponse> handleDomainException(DomainException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorMainResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // OWASP: Handle malformed/unreadable JSON bodies (e.g. invalid date formats) without leaking internals
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Map<String, String> errors = new HashMap<>();
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            String fieldName = ife.getPath().get(0).getFieldName();
            String targetType = ife.getTargetType() != null ? ife.getTargetType().getSimpleName() : "unknown";
            errors.put(fieldName, "Invalid value '" + ife.getValue() + "' for field '" + fieldName
                    + "'. Expected type: " + targetType + ".");
        } else {
            errors.put("request", "Malformed or unreadable request body.");
        }
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // Generic exception handler for unhandled exceptions - OWASP: Don't leak sensitive information
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorMainResponse> handleException(Exception ex) {
       ErrorMainResponse errorResponse = new ErrorMainResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
           "An unexpected error occurred. Please contact support if the problem persists.");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
