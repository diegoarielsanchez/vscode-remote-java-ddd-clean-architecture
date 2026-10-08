package com.das.infra.service.settlement;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;

/**
 * HTTP client for msr-service's active-status endpoint — the fallback used by
 * {@link MedicalSalesRepStatusAdapter} for reps not yet in the local snapshot. It calls the
 * msr-service REST API. The injected {@link RestTemplate} is {@code @LoadBalanced},
 * so {@code msrServiceBaseUrl} is resolved as an Eureka logical service name — requests
 * are distributed across every registered medical-sales-rep-service instance. Integration
 * tests can still point it at a stub/WireMock server by overriding it with a raw host:port.
 */
@Service
public class MedicalSalesRepHttpAdapter {

    private static final Logger log = LoggerFactory.getLogger(MedicalSalesRepHttpAdapter.class);

    private final RestTemplate restTemplate;
    private final String msrServiceBaseUrl;

    public MedicalSalesRepHttpAdapter(
            RestTemplate loadBalancedRestTemplate,
            @Value("${msr.service.base-url}") String msrServiceBaseUrl) {
        this.restTemplate = loadBalancedRestTemplate;
        this.msrServiceBaseUrl = msrServiceBaseUrl;
    }

    /**
     * Asks msr-service whether the rep is active. Returns the answer only when msr-service gave a
     * definite one (2xx); empty when the rep was not found or the call failed.
     */
    public Optional<Boolean> lookUp(MedicalSalesRepId medicalSalesRepId) {
        // Use the dedicated minimal endpoint — only the boolean active field is
        // returned, keeping PII in the MSR service. This endpoint is permitAll()
        // for inter-service calls (no user JWT required).
        String url = msrServiceBaseUrl + "/api/v1/medicalsalesrep/{id}/active-status";
        try {
            ResponseEntity<ActiveStatusResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, HttpEntity.EMPTY, ActiveStatusResponse.class,
                    medicalSalesRepId.value());
            ActiveStatusResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                return Optional.of(body.active());
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("MedicalSalesRep not found: {}", medicalSalesRepId.value());
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            log.error("Access denied when checking MedicalSalesRep status for id {} — check inter-service security config: {}",
                    medicalSalesRepId.value(), e.getStatusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error checking MedicalSalesRep status for id {}: {}", medicalSalesRepId.value(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Minimal projection of the MSR active-status endpoint.
     * Only the boolean {@code active} field is exposed — no PII.
     */
    private record ActiveStatusResponse(boolean active) {}
}
