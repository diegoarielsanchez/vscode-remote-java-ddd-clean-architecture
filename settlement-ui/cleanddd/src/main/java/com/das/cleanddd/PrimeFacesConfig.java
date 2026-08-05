package com.das.cleanddd;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.security.AuthTokenInterceptor;

@Configuration
public class PrimeFacesConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(AuthTokenInterceptor authTokenInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(authTokenInterceptor);
        return restTemplate;
    }

    // Separate bean (no auth interceptor) for the identity-service login call itself.
    @Bean
    @LoadBalanced
    public RestTemplate identityRestTemplate() {
        return new RestTemplate();
    }
}
