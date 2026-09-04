package com.das.infra.service.order;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;

/**
 * Infra adapter implementing {@link IMedicalSalesRepValidator} by calling
 * msr-service's minimal, unauthenticated active-status endpoint (Option A ACL —
 * see product-catalog-service's plan for why no local snapshot cache is kept
 * here, unlike visit-service's heavier variant of this same pattern).
 */
@Service
public class MedicalSalesRepValidatorAdapter implements IMedicalSalesRepValidator {

    private static final Logger log = LoggerFactory.getLogger(MedicalSalesRepValidatorAdapter.class);
    private static final String MSR_BASE_URL = "http://medical-sales-rep-service";

    private final RestTemplate restTemplate;

    public MedicalSalesRepValidatorAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public boolean existsAndActive(String medicalSalesRepId) {
        if (medicalSalesRepId == null || medicalSalesRepId.isBlank()) {
            return false;
        }
        try {
            ResponseEntity<ActiveStatusResponse> response = restTemplate.exchange(
                    MSR_BASE_URL + "/api/v1/medicalsalesrep/{id}/active-status",
                    Objects.requireNonNull(HttpMethod.GET),
                    HttpEntity.EMPTY,
                    ActiveStatusResponse.class,
                    medicalSalesRepId);
            return response.getStatusCode().is2xxSuccessful()
                    && Optional.ofNullable(response.getBody())
                        .map(ActiveStatusResponse::active)
                        .orElse(false);
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("MSR not found: {}", medicalSalesRepId);
            return false;
        } catch (Exception e) {
            log.error("Failed to check MSR active status for id={}: {}", medicalSalesRepId, e.getMessage());
            return false;
        }
    }

    private record ActiveStatusResponse(boolean active) {}
}
