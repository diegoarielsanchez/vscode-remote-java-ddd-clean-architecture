package com.das.infra.service.visit;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SQLVisitRepository}, in particular the address mapping —
 * this repository previously had no test coverage at all.
 *
 * Uses {@code @DataJpaTest} to spin up an in-memory H2 database and auto-configure JPA.
 * The repository adapter is imported explicitly because it is a {@code @Service}, not a
 * {@code @Repository}, so it is not included in the default JPA slice scan.
 */
@DataJpaTest
@Import(SQLVisitRepository.class)
class SQLVisitRepositoryTest {

    /** Yesterday at 10:00 — in the past and within Visit's 1-month validation window. */
    private static final LocalDateTime VALID_DATE = LocalDateTime.now().minusDays(1).withHour(10);

    @Autowired
    private SQLVisitRepository repository;

    @Autowired
    private VisitJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    private Visit aVisit(String id, AddressValueObject address) throws BusinessValidationException {
        return new Visit(
                new VisitId(id),
                new VisitDateTime(VALID_DATE),
                new HealthCareProfId(UUID.randomUUID().toString()),
                new TextValueObject("routine check") {},
                new Identifier(UUID.randomUUID().toString()) {},
                List.of(),
                new MedicalSalesRepId(UUID.randomUUID().toString()),
                address);
    }

    @Test
    void save_persistsAddress_whenPresent() throws BusinessValidationException {
        String id = UUID.randomUUID().toString();
        AddressValueObject address = new AddressValueObject("1 Clinic Rd", "Springfield", "IL", "62701", "USA");

        repository.save(aVisit(id, address));

        VisitEntity stored = jpaRepository.findById(Objects.requireNonNull(id)).orElseThrow();
        assertThat(stored.getAddressStreet()).isEqualTo("1 Clinic Rd");
        assertThat(stored.getAddressCity()).isEqualTo("Springfield");
        assertThat(stored.getAddressState()).isEqualTo("IL");
        assertThat(stored.getAddressPostalCode()).isEqualTo("62701");
        assertThat(stored.getAddressCountry()).isEqualTo("USA");
    }

    @Test
    void search_roundTripsAddress() throws BusinessValidationException {
        String id = UUID.randomUUID().toString();
        AddressValueObject address = new AddressValueObject("1 Clinic Rd", "Springfield", "IL", "62701", "USA");
        repository.save(aVisit(id, address));

        Optional<Visit> found = repository.search(new VisitId(id));

        assertThat(found).isPresent();
        assertThat(found.get().address()).isEqualTo(address);
    }

    @Test
    void search_returnsNullAddress_whenNoneWasEverSaved() throws BusinessValidationException {
        String id = UUID.randomUUID().toString();
        repository.save(aVisit(id, null));

        Optional<Visit> found = repository.search(new VisitId(id));

        assertThat(found).isPresent();
        assertThat(found.get().address()).isNull();
    }
}
