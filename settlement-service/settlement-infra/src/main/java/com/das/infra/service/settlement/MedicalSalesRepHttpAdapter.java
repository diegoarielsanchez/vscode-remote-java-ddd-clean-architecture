package com.das.infra.service.settlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

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
        String url = msrServiceBaseUrl + "/api/v1/medicalsalesrep/get";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> requestEntity =
                    new HttpEntity<>(Map.of("medicalSalesRepId", medicalSalesRepId.value()), headers);
            ResponseEntity<MedicalSalesRepResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, MedicalSalesRepResponse.class);
            MedicalSalesRepResponse body = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                return Boolean.TRUE.equals(body.active());
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
