package com.das.settlement.application.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a {@link RestTemplate} bean decorated with {@code @LoadBalanced}.
 *
 * <p>The {@code @LoadBalanced} annotation integrates Spring Cloud LoadBalancer so that
 * logical service names (e.g. {@code http://medical-sales-rep-service/...}) are resolved
 * through the Eureka registry at call time, instead of a hard-coded host:port.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        // HttpComponentsClientHttpRequestFactory (Apache HttpClient 5) is required so
        // RestTemplate.exchange() with HttpMethod.GET properly sends a request body.
        // The default SimpleClientHttpRequestFactory (HttpURLConnection) silently
        // drops the body on GET requests, causing the MSR service to return 400.
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }
}
