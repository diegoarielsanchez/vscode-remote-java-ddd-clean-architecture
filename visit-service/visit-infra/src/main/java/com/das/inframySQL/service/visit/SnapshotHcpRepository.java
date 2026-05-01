package com.das.inframySQL.service.visit;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfActive;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.shared.criteria.Criteria;

/**
 * Option B — primary HCP repository backed by local snapshot table.
 * On cache-miss for {@link #findById}, falls back to the HTTP repository
 * and seeds the snapshot so subsequent reads stay local.
 */
@Primary
@Service
public class SnapshotHcpRepository implements IHealthCareProfRepository {

    private static final Logger log = LoggerFactory.getLogger(SnapshotHcpRepository.class);

    private final HcpSnapshotJpaRepository jpaRepo;
    private final IHealthCareProfRepository httpFallback;

    public SnapshotHcpRepository(
            HcpSnapshotJpaRepository jpaRepo,
            @Qualifier("httpHealthCareProfRepository") IHealthCareProfRepository httpFallback) {
        this.jpaRepo = jpaRepo;
        this.httpFallback = httpFallback;
    }

    @Override
    public Optional<HealthCareProf> findById(HealthCareProfId id) {
        if (id == null || id.value() == null) {
            return Optional.empty();
        }
        Optional<HcpSnapshotEntity> cached = jpaRepo.findById(id.value());
        if (cached.isPresent()) {
            return cached.map(this::toDomain);
        }
        // Cache-miss: fall back to HTTP and seed snapshot for future reads
        Optional<HealthCareProf> fromHttp = httpFallback.findById(id);
        fromHttp.ifPresent(hcp -> {
            try {
                jpaRepo.save(toEntity(hcp));
            } catch (Exception e) {
                log.warn("Failed to seed HCP snapshot for id={}: {}", id.value(), e.getMessage());
            }
        });
        return fromHttp;
    }

    /** Called by {@link HcpSnapshotUpdater} and {@link SnapshotBootstrapService}. */
    @Override
    public void save(HealthCareProf hcp) {
        if (hcp == null || hcp.getId() == null) {
            return;
        }
        jpaRepo.save(toEntity(hcp));
    }

    @Override
    public List<HealthCareProf> searchAll() {
        return jpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<HealthCareProf> findByName(HealthCareProfName n, HealthCareProfName s, int page, int pageSize) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    @Override
    public List<HealthCareProf> findBySpecialty(String code, int page, int pageSize) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    @Override
    public List<HealthCareProf> matching(Criteria c) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    @Override
    public Optional<HealthCareProf> findByEmail(HealthCareProfEmail email) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    HcpSnapshotEntity toEntity(HealthCareProf hcp) {
        HcpSnapshotEntity e = new HcpSnapshotEntity();
        e.setId(hcp.getId().value());
        e.setName(hcp.getName() != null ? hcp.getName().value() : null);
        e.setSurname(hcp.getSurname() != null ? hcp.getSurname().value() : null);
        e.setEmail(hcp.getEmail() != null ? hcp.getEmail().value() : null);
        e.setActive(hcp.getActive() != null ? hcp.getActive().value() : null);
        if (hcp.getSpecialties() != null && !hcp.getSpecialties().isEmpty()) {
            String specialties = hcp.getSpecialties().stream()
                    .map(Specialty::code)
                    .collect(Collectors.joining(","));
            e.setSpecialties(specialties);
        }
        return e;
    }

    private HealthCareProf toDomain(HcpSnapshotEntity e) {
        // Specialties are not required for visit-plan operations; pass empty list
        List<Specialty> specialties = List.of();
        return new HealthCareProf(
                new HealthCareProfId(e.getId()),
                new HealthCareProfName(e.getName()),
                new HealthCareProfName(e.getSurname()),
                new HealthCareProfEmail(e.getEmail()),
                new HealthCareProfActive(e.getActive()),
                specialties);
    }
}
