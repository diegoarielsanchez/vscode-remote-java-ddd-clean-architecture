package com.das.infra.service.healthcareprof;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfActive;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.shared.AddressValueObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SQLHealthCareProfRepository}.
 *
 * Uses {@code @DataJpaTest} to spin up an in-memory H2 database and auto-configure
 * JPA. The repository adapter is imported explicitly because it is a {@code @Service},
 * not a {@code @Repository}, so it is not included in the default JPA slice scan.
 */
@DataJpaTest
@Import(SQLHealthCareProfRepository.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SQLHealthCareProfRepositoryTest {

    @Autowired
    private SQLHealthCareProfRepository repository;

    @Autowired
    private HealthCareProfJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private HealthCareProf anHcp(String id, String firstName, String lastName,
                                  String email, boolean active) {
        return new HealthCareProf(
                new HealthCareProfId(id),
                new HealthCareProfName(firstName),
                new HealthCareProfName(lastName),
                new HealthCareProfEmail(email),
                new HealthCareProfActive(active),
                List.of());
    }

    private HealthCareProf anHcpWithSpecialties(String id, String firstName, String lastName,
                                                 String email, boolean active,
                                                 List<Specialty> specialties) {
        return new HealthCareProf(
                new HealthCareProfId(id),
                new HealthCareProfName(firstName),
                new HealthCareProfName(lastName),
                new HealthCareProfEmail(email),
                new HealthCareProfActive(active),
                specialties);
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_persistsNewHcp() {
        String id = UUID.randomUUID().toString();

        repository.save(anHcp(id, "Alice", "Smith", "alice@example.com", true));

        Optional<HealthCareProfEntity> stored = jpaRepository.findById(Objects.requireNonNull(id));
        assertThat(stored).isPresent();
        assertThat(stored.get().getName()).isEqualTo("Alice");
        assertThat(stored.get().getSurname()).isEqualTo("Smith");
        assertThat(stored.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(stored.get().getActive()).isTrue();
    }

    @Test
    void save_updatesExistingRecord_whenSameIdSavedAgain() {
        String id = UUID.randomUUID().toString();
        repository.save(anHcp(id, "Alice", "Smith", "alice@example.com", true));

        repository.save(anHcp(id, "Alice", "Smith", "updated@example.com", false));

        HealthCareProfEntity stored =
            jpaRepository.findById(Objects.requireNonNull(id)).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("updated@example.com");
        assertThat(stored.getActive()).isFalse();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsPersistedHcp() {
        String id = UUID.randomUUID().toString();
        repository.save(anHcp(id, "Bob", "Jones", "bob@example.com", true));

        Optional<HealthCareProf> result = repository.findById(new HealthCareProfId(id));

        assertThat(result).isPresent();
        assertThat(result.get().id().value()).isEqualTo(id);
        assertThat(result.get().getName().value()).isEqualTo("Bob");
        assertThat(result.get().getSurname().value()).isEqualTo("Jones");
        assertThat(result.get().getEmail().value()).isEqualTo("bob@example.com");
        assertThat(result.get().isActive()).isTrue();
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        Optional<HealthCareProf> result =
                repository.findById(new HealthCareProfId(UUID.randomUUID().toString()));

        assertThat(result).isEmpty();
    }

    @Test
    void findById_returnsEmpty_whenIdentifierIsNull() {
        Optional<HealthCareProf> result = repository.findById(null);

        assertThat(result).isEmpty();
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmail_returnsPersistedHcp() {
        String id = UUID.randomUUID().toString();
        repository.save(anHcp(id, "Carol", "White", "carol@example.com", false));

        Optional<HealthCareProf> result =
                repository.findByEmail(new HealthCareProfEmail("carol@example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().id().value()).isEqualTo(id);
    }

    @Test
    void findByEmail_returnsEmpty_whenNotFound() {
        Optional<HealthCareProf> result =
                repository.findByEmail(new HealthCareProfEmail("nobody@example.com"));

        assertThat(result).isEmpty();
    }

    // ── searchAll ─────────────────────────────────────────────────────────────

    @Test
    void searchAll_returnsAllPersistedHcps() {
        repository.save(anHcp(UUID.randomUUID().toString(), "Dave", "Brown", "dave@example.com", true));
        repository.save(anHcp(UUID.randomUUID().toString(), "Eve", "Green", "eve@example.com", false));

        List<HealthCareProf> all = repository.searchAll();

        assertThat(all).hasSize(2);
        assertThat(all)
                .extracting(hcp -> hcp.getEmail().value())
                .containsExactlyInAnyOrder("dave@example.com", "eve@example.com");
    }

    @Test
    void searchAll_returnsEmptyList_whenNoHcpsExist() {
        assertThat(repository.searchAll()).isEmpty();
    }

    // ── findByName ────────────────────────────────────────────────────────────

    @Test
    void findByName_returnsMatchingHcp_whenBothNameAndSurnameProvided() {
        repository.save(anHcp(UUID.randomUUID().toString(), "Frank", "Miller", "frank@example.com", true));
        repository.save(anHcp(UUID.randomUUID().toString(), "Grace", "Miller", "grace@example.com", true));

        List<HealthCareProf> results = repository.findByName(
                new HealthCareProfName("Frank"),
                new HealthCareProfName("Miller"),
                1, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName().value()).isEqualTo("Frank");
    }

    @Test
    void findByName_returnsFirstPage_whenResultsExceedPageSize() {
        for (int i = 0; i < 5; i++) {
            repository.save(anHcp(
                    UUID.randomUUID().toString(),
                    "Henry", "Taylor",
                    "henry" + i + "@example.com",
                    true));
        }

        List<HealthCareProf> page1 = repository.findByName(
                new HealthCareProfName("Henry"),
                new HealthCareProfName("Taylor"),
                1, 3);

        assertThat(page1).hasSize(3);
    }

    @Test
    void findByName_returnsLastPage_withRemainingResults() {
        for (int i = 0; i < 5; i++) {
            repository.save(anHcp(
                    UUID.randomUUID().toString(),
                    "Henry", "Taylor",
                    "henry" + i + "@example.com",
                    true));
        }

        List<HealthCareProf> page2 = repository.findByName(
                new HealthCareProfName("Henry"),
                new HealthCareProfName("Taylor"),
                2, 3);

        assertThat(page2).hasSize(2);
    }

    // ── findBySpecialty ───────────────────────────────────────────────────────

    @Test
    void findBySpecialty_returnsHcpsWithMatchingSpecialtyCode() {
        Specialty cardiology = new Specialty("CARD", "Cardiology");
        Specialty neurology  = new Specialty("NEUR", "Neurology");

        repository.save(anHcpWithSpecialties(
                UUID.randomUUID().toString(), "Iris", "Reed", "iris@example.com", true,
                List.of(cardiology)));
        repository.save(anHcpWithSpecialties(
                UUID.randomUUID().toString(), "Jack", "Fox", "jack@example.com", true,
                List.of(neurology)));

        List<HealthCareProf> results = repository.findBySpecialty("CARD", 1, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEmail().value()).isEqualTo("iris@example.com");
    }

    @Test
    void findBySpecialty_returnsEmpty_whenNoHcpHasThatSpecialty() {
        repository.save(anHcp(UUID.randomUUID().toString(), "Kim", "Lane", "kim@example.com", true));

        List<HealthCareProf> results = repository.findBySpecialty("DERM", 1, 10);

        assertThat(results).isEmpty();
    }

    @Test
    void findBySpecialty_respectsPageSize() {
        Specialty cardiology = new Specialty("CARD", "Cardiology");
        String[] surnames = {"Adams", "Baker", "Clark", "Davis", "Evans"};
        for (int i = 0; i < 5; i++) {
            repository.save(anHcpWithSpecialties(
                    UUID.randomUUID().toString(), "Doc", surnames[i], "doc" + i + "@example.com", true,
                    List.of(cardiology)));
        }

        List<HealthCareProf> page1 = repository.findBySpecialty("CARD", 1, 2);

        assertThat(page1).hasSize(2);
    }

    // ---------------------------------------------------------------------------
    // addresses
    // ---------------------------------------------------------------------------

    @Test
    void save_persistsMultipleAddresses() {
        String id = UUID.randomUUID().toString();
        AddressValueObject officeOne = new AddressValueObject("100 Hospital Dr", "Boston", "MA", "02114", "USA");
        AddressValueObject officeTwo = new AddressValueObject("200 Clinic Ave", "Cambridge", "MA", "02139", "USA");
        HealthCareProf hcp = new HealthCareProf(
                new HealthCareProfId(id),
                new HealthCareProfName("Doc"),
                new HealthCareProfName("Smith"),
                new HealthCareProfEmail("doc@example.com"),
                new HealthCareProfActive(true),
                List.of(),
                List.of(officeOne, officeTwo));

        repository.save(hcp);

        HealthCareProfEntity stored = jpaRepository.findById(Objects.requireNonNull(id)).orElseThrow();
        assertThat(stored.getAddresses()).hasSize(2);
    }

    @Test
    void findById_roundTripsAddresses_includingOneWithNoState() {
        String id = UUID.randomUUID().toString();
        // No state — France doesn't use one. Also exercises the pipe-delimited encoding's
        // empty middle field, the exact spot a decode-side split() bug would show up.
        AddressValueObject noState = new AddressValueObject("Rue de Rivoli", "Paris", null, "75001", "France");
        AddressValueObject withState = new AddressValueObject("100 Hospital Dr", "Boston", "MA", "02114", "USA");
        repository.save(new HealthCareProf(
                new HealthCareProfId(id),
                new HealthCareProfName("Doc"),
                new HealthCareProfName("Smith"),
                new HealthCareProfEmail("doc@example.com"),
                new HealthCareProfActive(true),
                List.of(),
                List.of(noState, withState)));

        HealthCareProf found = repository.findById(new HealthCareProfId(id)).orElseThrow();

        assertThat(found.getAddresses()).containsExactlyInAnyOrder(noState, withState);
        assertThat(found.getAddresses()).filteredOn(a -> a.country().equals("France"))
                .extracting(AddressValueObject::state)
                .containsExactly((String) null);
    }

    @Test
    void findById_returnsEmptyAddressList_whenNoneWasEverSaved() {
        String id = UUID.randomUUID().toString();
        repository.save(anHcp(id, "Doc", "Smith", "doc@example.com", true));

        HealthCareProf found = repository.findById(new HealthCareProfId(id)).orElseThrow();

        assertThat(found.getAddresses()).isEmpty();
    }
}
