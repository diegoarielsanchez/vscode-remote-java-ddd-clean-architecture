package com.das.hcp.application.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.lang.NonNull;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORITIES_CLAIM = "authorities";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);
            if (jwt != null && validateToken(jwt)) {
                Authentication authentication = getAuthenticationFromToken(jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException e) {
            logger.error("JWT validation failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Cannot set user authentication in security context", e);
        }
        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(new javax.crypto.spec.SecretKeySpec(
                    jwtSecret.getBytes(), 0, jwtSecret.getBytes().length, "HmacSHA256"))
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("JWT token validation failed", e);
            return false;
        }
    }

    private Authentication getAuthenticationFromToken(String token) {
        Claims claims = extractClaimsFromToken(token);
        String username = claims.getSubject();

        @SuppressWarnings("unchecked")
        List<String> authoritiesStrings = (List<String>) claims.get(AUTHORITIES_CLAIM, List.class);
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (authoritiesStrings != null) {
            for (String authority : authoritiesStrings) {
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    private Claims extractClaimsFromToken(String token) {
        return Jwts.parser()
            .verifyWith(new javax.crypto.spec.SecretKeySpec(
                jwtSecret.getBytes(), 0, jwtSecret.getBytes().length, "HmacSHA256"))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractUsername(String token) {
        return extractClaimsFromToken(token).getSubject();
    }
}
