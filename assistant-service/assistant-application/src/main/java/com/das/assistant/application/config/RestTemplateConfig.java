package com.das.assistant.application.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a {@link RestTemplate} bean decorated with {@code @LoadBalanced} so
 * logical service names (e.g. {@code http://product-catalog-service/...})
 * are resolved through the Eureka registry at call time.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
}
