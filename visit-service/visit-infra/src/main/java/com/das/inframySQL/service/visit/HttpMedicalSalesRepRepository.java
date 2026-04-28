package com.das.inframySQL.service.visit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepActive;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.shared.criteria.Criteria;

/**
 * Option A — Anti-Corruption Layer for MSR bounded context.
 * Calls the msr-service REST API instead of accessing its database directly.
 */
@Service
public class HttpMedicalSalesRepRepository implements IMedicalSalesRepRepository {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public HttpMedicalSalesRepRepository(
            @Value("${services.msr.base-url:http://localhost:8086}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<MedicalSalesRep> findById(MedicalSalesRepId id) {
        if (id == null || id.value() == null) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String token = extractAuthorizationHeader();
            if (token != null) {
                headers.set("Authorization", token);
            }
            HttpEntity<Map<String, String>> request =
                    new HttpEntity<>(Map.of("id", id.value()), headers);
            ResponseEntity<MsrResponse> response = restTemplate.exchange(
                    baseUrl + "/api/v1/medicalsalesrep/get",
                    HttpMethod.GET,
                    request,
                    MsrResponse.class);
            if (response.getBody() == null) {
                return Optional.empty();
            }
            return Optional.of(toMedicalSalesRep(response.getBody()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private MedicalSalesRep toMedicalSalesRep(MsrResponse r) {
        return new MedicalSalesRep(
                new MedicalSalesRepId(r.id()),
                new MedicalSalesRepName(r.name()),
                new MedicalSalesRepName(r.surname()),
                new MedicalSalesRepEmail(r.email()),
                new MedicalSalesRepActive(r.active()));
    }

    private String extractAuthorizationHeader() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getHeader("Authorization") : null;
        } catch (Exception e) {
            return null;
        }
    }

    record MsrResponse(String id, String name, String surname, String email, Boolean active) {}

    // ── Unsupported operations (MSR data is owned by msr-service) ──────────

    @Override
    public void save(MedicalSalesRep m) {
        throw new UnsupportedOperationException("MSR data is owned by msr-service");
    }

    @Override
    public List<MedicalSalesRep> findByName(MedicalSalesRepName n, MedicalSalesRepName s, int p, int ps) {
        throw new UnsupportedOperationException("MSR data is owned by msr-service");
    }

    @Override
    public List<MedicalSalesRep> matching(Criteria c) {
        throw new UnsupportedOperationException("MSR data is owned by msr-service");
    }

    @Override
    public List<MedicalSalesRep> searchAll() {
        throw new UnsupportedOperationException("MSR data is owned by msr-service");
    }

    @Override
    public Optional<MedicalSalesRep> findByEmail(MedicalSalesRepEmail e) {
        throw new UnsupportedOperationException("MSR data is owned by msr-service");
    }
}
