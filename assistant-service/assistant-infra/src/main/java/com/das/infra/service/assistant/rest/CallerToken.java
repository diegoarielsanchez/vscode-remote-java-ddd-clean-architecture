package com.das.infra.service.assistant.rest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the rep's own {@code Authorization} header, so every call the assistant makes to another service is
 * made as the rep, with the rep's rights and nothing more. The assistant has no account of its own.
 */
final class CallerToken {

    private CallerToken() {
    }

    static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = authorizationHeader();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, token);
        }
        return headers;
    }

    static <T> HttpEntity<T> entity(T body) {
        return new HttpEntity<>(body, headers());
    }

    private static String authorizationHeader() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest().getHeader(HttpHeaders.AUTHORIZATION)
                : null;
    }
}
