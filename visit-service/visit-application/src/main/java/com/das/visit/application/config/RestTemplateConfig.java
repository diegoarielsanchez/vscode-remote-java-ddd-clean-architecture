package com.das.visit.application.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a {@link RestTemplate} bean decorated with {@code @LoadBalanced}.
 *
 * <p>The {@code @LoadBalanced} annotation integrates Spring Cloud LoadBalancer
 * so that logical service names (e.g. {@code http://medical-sales-rep-service/...})
 * are resolved through the Eureka registry at call time, instead of using
 * hard-coded host:port values in {@code application.properties}.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        return new RestTemplate();
    }
}
