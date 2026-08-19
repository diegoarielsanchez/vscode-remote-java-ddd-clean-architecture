package com.das.infra.service.visit;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfActive;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;

/**
 * Option A — Anti-Corruption Layer for HCP bounded context.
 * Calls the hcp-service REST API via the Eureka-registered service name,
 * resolved at call time by the {@code @LoadBalanced} {@link RestTemplate} bean.
 */
@Service
public class HttpHealthCareProfRepository implements IHealthCareProfRepository {

    private static final String BASE_URL = "http://healthcare-prof-service";

    private final RestTemplate restTemplate;

    public HttpHealthCareProfRepository(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public Optional<HealthCareProf> findById(HealthCareProfId id) {
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
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<HcpResponse> response = restTemplate.exchange(
                    BASE_URL + "/api/v1/healthcareprof/" + id.value(),
                    HttpMethod.valueOf("GET"),
                    request,
                    HcpResponse.class);
            if (response.getBody() == null) {
                return Optional.empty();
            }
            return Optional.of(toHealthCareProf(response.getBody()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            return Optional.empty();
        }
    }

    private HealthCareProf toHealthCareProf(HcpResponse r) {
        List<Specialty> specialties = r.specialties() == null
                ? List.of()
                : r.specialties().stream()
                        .map(name -> {
                            try {
                                return new Specialty(name, name);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(s -> s != null)
                        .toList();
        return new HealthCareProf(
                new HealthCareProfId(r.id()),
                new HealthCareProfName(r.name()),
                new HealthCareProfName(r.surname()),
                new HealthCareProfEmail(r.email()),
                new HealthCareProfActive(r.active()),
                specialties);
    }

    private String extractAuthorizationHeader() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getHeader("Authorization") : null;
        } catch (Exception e) {
            return null;
        }
    }

    record HcpResponse(String id, String name, String surname, String email, Boolean active, List<String> specialties) {}

    // ── Unsupported operations (HCP data is owned by hcp-service) ──────────

    @Override
    public void save(HealthCareProf h) {
        throw new UnsupportedOperationException("HCP data is owned by hcp-service");
    }

    @Override
    public List<HealthCareProf> findByName(HealthCareProfName n, HealthCareProfName s, int p, int ps) {
        throw new UnsupportedOperationException("HCP data is owned by hcp-service");
    }

    @Override
    public List<HealthCareProf> findBySpecialty(String code, int p, int ps) {
        throw new UnsupportedOperationException("HCP data is owned by hcp-service");
    }

    @Override
    public List<HealthCareProf> matching(Criteria c) {
        throw new UnsupportedOperationException("HCP data is owned by hcp-service");
    }

    @Override
    public List<HealthCareProf> searchAll() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String token = extractAuthorizationHeader();
            if (token != null) {
                headers.set("Authorization", token);
            }
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<HcpResponse[]> response = restTemplate.exchange(
                    BASE_URL + "/api/v1/healthcareprof/list?firstName=&lastName=&page=1&pageSize=10000",
                    HttpMethod.valueOf("POST"),
                    request,
                    HcpResponse[].class);
            if (response.getBody() == null) {
                return List.of();
            }
            return Arrays.stream(response.getBody())
                    .map(this::toHealthCareProf)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Optional<HealthCareProf> findByEmail(HealthCareProfEmail e) {
        throw new UnsupportedOperationException("HCP data is owned by hcp-service");
    }
}
