package com.das.infra.service.visit;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;

/**
 * Infra adapter that implements {@link IHealthCareProfValidator} by calling
 * the hcp-service's minimal active-status endpoint.
 *
 * <p>Only a boolean is transferred — no PII — keeping the visit service
 * decoupled from the HCP bounded context (OWASP data minimisation, DDD ACL).
 * The endpoint is permitAll() on the HCP side so no user JWT is forwarded,
 * preventing credential coupling between microservices.
 *
 * <p>A local {@link HcpSnapshotEntity} stores the last-known active flag as
 * a resilience fallback when the HCP service is temporarily unavailable.
 */
@Service
public class HealthCareProfValidatorAdapter implements IHealthCareProfValidator {

    private static final Logger log = LoggerFactory.getLogger(HealthCareProfValidatorAdapter.class);

    private static final String HCP_BASE_URL = "http://healthcare-prof-service";

    private final HcpSnapshotJpaRepository hcpSnapshotJpaRepository;
    private final RestTemplate restTemplate;

    public HealthCareProfValidatorAdapter(
            HcpSnapshotJpaRepository hcpSnapshotJpaRepository,
            RestTemplate loadBalancedRestTemplate) {
        this.hcpSnapshotJpaRepository = hcpSnapshotJpaRepository;
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public boolean existsAndActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        try {
            ResponseEntity<ActiveStatusResponse> response = restTemplate.exchange(
                    HCP_BASE_URL + "/api/v1/healthcareprof/{id}/active-status",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    ActiveStatusResponse.class,
                    id);
            boolean active = response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().active();

            // Keep the local snapshot's active flag in sync for the fallback path.
            HcpSnapshotEntity entity = hcpSnapshotJpaRepository.findById(id).orElse(new HcpSnapshotEntity());
            entity.setId(id);
            entity.setActive(active);
            hcpSnapshotJpaRepository.save(entity);
            log.info("HCP active-status check via HTTP: id={} active={}", id, active);

            return active;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("HCP not found: {}", id);
            return false;
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            log.error("Access denied when checking HCP active status for id {} — check inter-service security config: {}",
                    id, e.getStatusCode());
            return false;
        } catch (Exception e) {
            // HTTP call failed — fall back to the local snapshot to avoid blocking
            // writes when the HCP service is temporarily unavailable.
            log.warn("HTTP call for HCP id={} failed, falling back to local snapshot: {}", id, e.getMessage());
            Optional<HcpSnapshotEntity> snapshot = hcpSnapshotJpaRepository.findById(id);
            if (snapshot.isPresent()) {
                return Boolean.TRUE.equals(snapshot.get().getActive());
            }
            return false;
        }
    }

    private record ActiveStatusResponse(boolean active) {}
}
