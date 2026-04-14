package com.das.cleanddd.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.das.cleanddd.application.security.JwtAuthenticationFilter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            // Disable CSRF for API (use JWT tokens instead)
            .csrf(csrf -> csrf.disable())

            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Configure session management
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configure authorization
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/loggers/**", "/actuator/logfile").hasRole("USER")
                .requestMatchers("/hello").permitAll()
                .requestMatchers("/health-check").permitAll()
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**"
                ).permitAll()
                // Allow unauthenticated access to auth endpoints
                .requestMatchers("/auth/**").permitAll()
                // API endpoints - require USER role
                .requestMatchers("/api/**").hasRole("USER")
                // All other requests
                .anyRequest().authenticated()
            )

            // Configure headers for security
            .headers(headers -> headers
                .contentTypeOptions(contentTypeOptions -> {})
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .requestMatcher(anyRequest -> true)) // includeSubdomains not available in Spring Security 6.x
                .frameOptions(frameOptions -> frameOptions.deny())
                .referrerPolicy(referrerPolicy -> referrerPolicy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
                .permissionsPolicyHeader(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=()"))
            );

        // Register JWT authentication filter before the username/password filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("https://*.yourdomain.com", "http://localhost:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/auth/**", configuration);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Profile("dev")
    public UserDetailsService userDetailsServiceDev(PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("Apatehia65$"))
                .roles("USER")
                .build();
        
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    @Profile("!dev")
    public UserDetailsService userDetailsServiceNonDev(
        @Value("${app.security.user.name}") String username,
        @Value("${app.security.user.password-hash}") String passwordHash,
        @Value("${app.security.user.roles:USER}") String[] roles
    ) {
        UserDetails user = User.withUsername(username)
            .password(passwordHash)
            .roles(roles)
            .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<?> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var bean = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    // Data initializer not needed with InMemoryUserDetailsManager
    // Users are now defined directly in the userDetailsService bean
}