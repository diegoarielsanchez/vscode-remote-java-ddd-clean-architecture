package com.das.infra.service.healthcareprof.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.infra.service.healthcareprof.HealthCareProfJpaRepository;
import com.das.infra.service.healthcareprof.SQLHealthCareProfRepository;
import com.das.infra.service.healthcareprof.SpringUnitOfWork;

/**
 * The outbox guarantee end to end on a real database: the aggregate row and its outbox rows are
 * committed together, or neither is. Runs without the test-managed transaction so commits and
 * rollbacks are real.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLHealthCareProfRepository.class, SpringUnitOfWork.class, OutboxHcpEventPublisher.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class UnitOfWorkAtomicityTest {

    @Autowired private UnitOfWork unitOfWork;
    @Autowired private SQLHealthCareProfRepository repository;
    @Autowired private HealthCareProfJpaRepository hcpRows;
    @Autowired private IHcpEventPublisher publisher;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        versions.deleteAll();
        hcpRows.deleteAll();
    }

    private static HealthCareProf newHcp() {
        return HealthCareProf.create(null, new HealthCareProfName("Ana"), new HealthCareProfName("Gomez"),
                new HealthCareProfEmail("ana@clinic.org"), null, List.of(new Specialty("CARD", "Cardiology")));
    }

    @Test
    void commitsTheAggregateAndItsEventsTogether() throws DomainException {
        HealthCareProf hcp = newHcp();

        unitOfWork.run(() -> {
            repository.save(hcp);
            hcp.pullDomainEvents().forEach(publisher::publish);
        });

        assertTrue(hcpRows.findById(hcp.getId().value()).isPresent());
        assertEquals(1, outbox.count());
        assertEquals("hcp.created", outbox.findAll().get(0).getEventType());
    }

    @Test
    void rollsBackBothWhenTheUseCaseFailsAfterRecordingEvents() {
        HealthCareProf hcp = newHcp();

        DomainException thrown = assertThrows(DomainException.class, () -> unitOfWork.run(() -> {
            repository.save(hcp);
            hcp.pullDomainEvents().forEach(publisher::publish);
            throw new DomainException("business rule violated after the event was recorded");
        }));

        assertEquals("business rule violated after the event was recorded", thrown.getMessage());
        assertTrue(hcpRows.findById(hcp.getId().value()).isEmpty(), "aggregate change must be rolled back");
        assertEquals(0, outbox.count(), "no event may survive for a change that did not happen");
    }
}
