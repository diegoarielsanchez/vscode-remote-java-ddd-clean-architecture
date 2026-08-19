package com.das.infra.service.visit;

import java.util.Optional;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;

/**
 * Infra adapter that implements {@link IMedicalSalesRepValidator} by calling
 * the msr-service's minimal active-status endpoint.
 *
 * <p>Only a boolean is transferred — no PII — keeping the visit service
 * decoupled from the MSR bounded context (OWASP data minimisation, DDD ACL).
 * The endpoint is permitAll() on the MSR side so no user JWT is forwarded,
 * preventing credential coupling between microservices.
 *
 * <p>A local {@link MsrSnapshotEntity} stores the last-known active flag as
 * a resilience fallback when the MSR service is temporarily unavailable.
 */
@Service
public class MedicalSalesRepValidatorAdapter implements IMedicalSalesRepValidator {

    private static final Logger log = LoggerFactory.getLogger(MedicalSalesRepValidatorAdapter.class);

    private static final String MSR_BASE_URL = "http://medical-sales-rep-service";

    private final MsrSnapshotJpaRepository msrSnapshotJpaRepository;
    private final RestTemplate restTemplate;

    public MedicalSalesRepValidatorAdapter(
            MsrSnapshotJpaRepository msrSnapshotJpaRepository,
            RestTemplate loadBalancedRestTemplate) {
        this.msrSnapshotJpaRepository = msrSnapshotJpaRepository;
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public boolean existsAndActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        try {
            ResponseEntity<ActiveStatusResponse> response = restTemplate.exchange(
                    MSR_BASE_URL + "/api/v1/medicalsalesrep/{id}/active-status",
                    Objects.requireNonNull(HttpMethod.GET),
                    HttpEntity.EMPTY,
                    ActiveStatusResponse.class,
                    id);
            boolean active = response.getStatusCode().is2xxSuccessful()
                        && Optional.ofNullable(response.getBody())
                            .map(status -> status.active())
                            .orElse(false);

            // Keep the local snapshot's active flag in sync for the fallback path.
            MsrSnapshotEntity entity = msrSnapshotJpaRepository.findById(id).orElse(new MsrSnapshotEntity());
            entity.setId(id);
            entity.setActive(active);
            msrSnapshotJpaRepository.save(entity);
            log.info("MSR active-status check via HTTP: id={} active={}", id, active);

            return active;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("MSR not found: {}", id);
            return false;
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            log.error("Access denied when checking MSR active status for id {} — check inter-service security config: {}",
                    id, e.getStatusCode());
            return false;
        } catch (Exception e) {
            // HTTP call failed — fall back to the local snapshot to avoid blocking
            // writes when the MSR service is temporarily unavailable.
            log.warn("HTTP call for MSR id={} failed, falling back to local snapshot: {}", id, e.getMessage());
            Optional<MsrSnapshotEntity> snapshot = msrSnapshotJpaRepository.findById(id);
            if (snapshot.isPresent()) {
                return Boolean.TRUE.equals(snapshot.get().getActive());
            }
            return false;
        }
    }

    private record ActiveStatusResponse(boolean active) {}
}
