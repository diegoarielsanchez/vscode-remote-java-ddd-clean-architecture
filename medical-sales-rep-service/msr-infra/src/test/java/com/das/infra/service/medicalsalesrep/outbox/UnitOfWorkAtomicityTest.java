package com.das.infra.service.medicalsalesrep.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.infra.service.medicalsalesrep.MedicalSalesRepJpaRepository;
import com.das.infra.service.medicalsalesrep.SQLMedicalSalesRepRepository;
import com.das.infra.service.medicalsalesrep.SpringUnitOfWork;

/**
 * The outbox guarantee end to end on a real database: the aggregate row and its outbox rows are
 * committed together, or neither is. Runs without the test-managed transaction so commits and
 * rollbacks are real.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLMedicalSalesRepRepository.class, SpringUnitOfWork.class, OutboxMsrEventPublisher.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class UnitOfWorkAtomicityTest {

    @Autowired private UnitOfWork unitOfWork;
    @Autowired private SQLMedicalSalesRepRepository repository;
    @Autowired private MedicalSalesRepJpaRepository msrRows;
    @Autowired private IMsrEventPublisher publisher;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        versions.deleteAll();
        msrRows.deleteAll();
    }

    private static MedicalSalesRep newMsr() {
        return MedicalSalesRep.create(null, new MedicalSalesRepName("Ana"), new MedicalSalesRepName("Gomez"),
                new MedicalSalesRepEmail("ana@pharma.com"), null);
    }

    @Test
    void commitsTheAggregateAndItsEventsTogether() throws DomainException {
        MedicalSalesRep msr = newMsr();

        unitOfWork.run(() -> {
            repository.save(msr);
            msr.pullDomainEvents().forEach(publisher::publish);
        });

        assertTrue(msrRows.findById(msr.getId().value()).isPresent());
        assertEquals(1, outbox.count());
        assertEquals("msr.created", outbox.findAll().get(0).getEventType());
    }

    @Test
    void rollsBackBothWhenTheUseCaseFailsAfterRecordingEvents() {
        MedicalSalesRep msr = newMsr();

        DomainException thrown = assertThrows(DomainException.class, () -> unitOfWork.run(() -> {
            repository.save(msr);
            msr.pullDomainEvents().forEach(publisher::publish);
            throw new DomainException("business rule violated after the event was recorded");
        }));

        assertEquals("business rule violated after the event was recorded", thrown.getMessage());
        assertTrue(msrRows.findById(msr.getId().value()).isEmpty(), "aggregate change must be rolled back");
        assertEquals(0, outbox.count(), "no event may survive for a change that did not happen");
    }
}
