package com.das.inframySQL.service.settlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.settlement.entities.IMedicalSalesRepPort;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;

/**
 * Infra adapter that implements {@link IMedicalSalesRepPort} by calling the
 * msr-service REST API.  The base URL is configurable so integration tests can
 * point it at a stub/WireMock server.
 */
@Service
public class MedicalSalesRepHttpAdapter implements IMedicalSalesRepPort {

    private static final Logger log = LoggerFactory.getLogger(MedicalSalesRepHttpAdapter.class);

    private final RestTemplate restTemplate;
    private final String msrServiceBaseUrl;

    public MedicalSalesRepHttpAdapter(
            RestTemplate restTemplate,
            @Value("${msr.service.base-url}") String msrServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.msrServiceBaseUrl = msrServiceBaseUrl;
    }

    @Override
    public boolean existsAndIsActive(MedicalSalesRepId medicalSalesRepId) {
        String url = msrServiceBaseUrl + "/api/v1/medicalsalesrep/" + medicalSalesRepId.value();
        try {
            ResponseEntity<MedicalSalesRepResponse> response =
                    restTemplate.getForEntity(url, MedicalSalesRepResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Boolean.TRUE.equals(response.getBody().active());
            }
            return false;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("MedicalSalesRep not found: {}", medicalSalesRepId.value());
            return false;
        } catch (Exception e) {
            log.error("Error checking MedicalSalesRep status for id {}: {}", medicalSalesRepId.value(), e.getMessage());
            return false;
        }
    }

    /**
     * Minimal projection of the MSR service response — only the {@code active} field
     * is needed for the existence+active check.
     */
    private record MedicalSalesRepResponse(String id, Boolean active) {}
}
