package com.das.settlement.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // HttpComponentsClientHttpRequestFactory (Apache HttpClient 5) is required so
        // RestTemplate.exchange() with HttpMethod.GET properly sends a request body.
        // The default SimpleClientHttpRequestFactory (HttpURLConnection) silently
        // drops the body on GET requests, causing the MSR service to return 400.
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }
}
