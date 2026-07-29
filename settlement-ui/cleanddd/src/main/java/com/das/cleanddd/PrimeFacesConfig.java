package com.das.cleanddd;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.security.AuthTokenInterceptor;

@Configuration
public class PrimeFacesConfig {

    @Bean
    public RestTemplate restTemplate(AuthTokenInterceptor authTokenInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(authTokenInterceptor);
        return restTemplate;
    }
}
