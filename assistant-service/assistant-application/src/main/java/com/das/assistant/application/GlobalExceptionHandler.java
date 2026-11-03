package com.das.assistant.application;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.das.cleanddd.domain.assistant.usecases.services.ConversationNotFoundException;
import com.das.cleanddd.domain.assistant.usecases.services.DraftNotFoundException;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.infra.service.assistant.claude.AssistantDisabledException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DraftNotFoundException.class, ConversationNotFoundException.class})
    public ResponseEntity<ErrorMainResponse> handleNotFound(DomainException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<ErrorMainResponse> handleBusinessValidation(BusinessValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AssistantDisabledException.class)
    public ResponseEntity<ErrorMainResponse> handleDisabled(AssistantDisabledException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** The model or another service could not be reached, or refused: the rep can try again later. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorMainResponse> handleDomainException(DomainException ex) {
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error ->
                errors.put(((FieldError) error).getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorMainResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorMainResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorMainResponse> handleRateLimitExceededException(RateLimitExceededException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    private static ResponseEntity<ErrorMainResponse> error(HttpStatus status, String message) {
        return new ResponseEntity<>(new ErrorMainResponse(status.value(), message), status);
    }
}
