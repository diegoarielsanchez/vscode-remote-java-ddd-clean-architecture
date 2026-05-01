package com.das.inframySQL.service.visit;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepActive;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.shared.criteria.Criteria;

/**
 * Option B — primary MSR repository backed by local snapshot table.
 * On cache-miss for {@link #findById}, falls back to the HTTP repository
 * and seeds the snapshot so subsequent reads stay local.
 */
@Primary
@Service
public class SnapshotMsrRepository implements IMedicalSalesRepRepository {

    private static final Logger log = LoggerFactory.getLogger(SnapshotMsrRepository.class);

    private final MsrSnapshotJpaRepository jpaRepo;
    private final IMedicalSalesRepRepository httpFallback;

    public SnapshotMsrRepository(
            MsrSnapshotJpaRepository jpaRepo,
            @Qualifier("httpMedicalSalesRepRepository") IMedicalSalesRepRepository httpFallback) {
        this.jpaRepo = jpaRepo;
        this.httpFallback = httpFallback;
    }

    @Override
    public Optional<MedicalSalesRep> findById(MedicalSalesRepId id) {
        if (id == null || id.value() == null) {
            return Optional.empty();
        }
        Optional<MsrSnapshotEntity> cached = jpaRepo.findById(id.value());
        if (cached.isPresent()) {
            return cached.map(this::toDomain);
        }
        // Cache-miss: fall back to HTTP and seed snapshot for future reads
        Optional<MedicalSalesRep> fromHttp = httpFallback.findById(id);
        fromHttp.ifPresent(msr -> {
            try {
                jpaRepo.save(toEntity(msr));
            } catch (Exception e) {
                log.warn("Failed to seed MSR snapshot for id={}: {}", id.value(), e.getMessage());
            }
        });
        return fromHttp;
    }

    /** Called by {@link MsrSnapshotUpdater} and {@link SnapshotBootstrapService}. */
    @Override
    public void save(MedicalSalesRep msr) {
        if (msr == null || msr.getId() == null) {
            return;
        }
        jpaRepo.save(toEntity(msr));
    }

    @Override
    public List<MedicalSalesRep> searchAll() {
        return jpaRepo.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<MedicalSalesRep> findByName(MedicalSalesRepName n, MedicalSalesRepName s, int page, int pageSize) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    @Override
    public List<MedicalSalesRep> matching(Criteria c) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    @Override
    public Optional<MedicalSalesRep> findByEmail(MedicalSalesRepEmail email) {
        throw new UnsupportedOperationException("Not supported by snapshot repository");
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    MsrSnapshotEntity toEntity(MedicalSalesRep msr) {
        MsrSnapshotEntity e = new MsrSnapshotEntity();
        e.setId(msr.getId().value());
        e.setName(msr.getName() != null ? msr.getName().value() : null);
        e.setSurname(msr.getSurname() != null ? msr.getSurname().value() : null);
        e.setEmail(msr.getEmail() != null ? msr.getEmail().value() : null);
        e.setActive(msr.getActive() != null ? msr.getActive().value() : null);
        return e;
    }

    private MedicalSalesRep toDomain(MsrSnapshotEntity e) {
        return new MedicalSalesRep(
                new MedicalSalesRepId(e.getId()),
                new MedicalSalesRepName(e.getName()),
                new MedicalSalesRepName(e.getSurname()),
                new MedicalSalesRepEmail(e.getEmail()),
                new MedicalSalesRepActive(e.getActive()));
    }
}
