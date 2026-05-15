package com.das.inframySQL.service.medicalsalesrep;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepActive;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MySQLMedicalSalesRepRepository}.
 *
 * Uses {@code @DataJpaTest} to spin up an in-memory H2 database and auto-configure
 * JPA. The repository adapter is imported explicitly because it is a {@code @Service},
 * not a {@code @Repository}, so it is not included in the default JPA slice scan.
 */
@DataJpaTest
@Import(MySQLMedicalSalesRepRepository.class)
class MySQLMedicalSalesRepRepositoryTest {

    @Autowired
    private MySQLMedicalSalesRepRepository repository;

    @Autowired
    private MedicalSalesRepJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private MedicalSalesRep aMsr(String id, String firstName, String lastName,
                                  String email, boolean active) {
        return new MedicalSalesRep(
                new MedicalSalesRepId(id),
                new MedicalSalesRepName(firstName),
                new MedicalSalesRepName(lastName),
                new MedicalSalesRepEmail(email),
                new MedicalSalesRepActive(active)
        );
    }

    // ---------------------------------------------------------------------------
    // save
    // ---------------------------------------------------------------------------

    @Test
    void save_persistsNewMedicalSalesRep() {
        String id = UUID.randomUUID().toString();

        repository.save(aMsr(id, "Alice", "Smith", "alice@example.com", false));

        Optional<MedicalSalesRepEntity> stored = jpaRepository.findById(id);
        assertThat(stored).isPresent();
        assertThat(stored.get().getName()).isEqualTo("Alice");
        assertThat(stored.get().getSurname()).isEqualTo("Smith");
        assertThat(stored.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(stored.get().getActive()).isFalse();
    }

    @Test
    void save_updatesExistingRecord_whenSameIdSavedAgain() {
        String id = UUID.randomUUID().toString();
        repository.save(aMsr(id, "Alice", "Smith", "alice@example.com", false));

        repository.save(aMsr(id, "Alice", "Smith", "updated@example.com", true));

        MedicalSalesRepEntity stored = jpaRepository.findById(id).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("updated@example.com");
        assertThat(stored.getActive()).isTrue();
    }

    // ---------------------------------------------------------------------------
    // findById
    // ---------------------------------------------------------------------------

    @Test
    void findById_returnsPersistedMsr() {
        String id = UUID.randomUUID().toString();
        repository.save(aMsr(id, "Bob", "Jones", "bob@example.com", true));

        Optional<MedicalSalesRep> result = repository.findById(new MedicalSalesRepId(id));

        assertThat(result).isPresent();
        assertThat(result.get().id().value()).isEqualTo(id);
        assertThat(result.get().getName().value()).isEqualTo("Bob");
        assertThat(result.get().getSurname().value()).isEqualTo("Jones");
        assertThat(result.get().getEmail().value()).isEqualTo("bob@example.com");
        assertThat(result.get().isActive()).isTrue();
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        Optional<MedicalSalesRep> result =
                repository.findById(new MedicalSalesRepId(UUID.randomUUID().toString()));

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // findByEmail
    // ---------------------------------------------------------------------------

    @Test
    void findByEmail_returnsPersistedMsr() {
        String id = UUID.randomUUID().toString();
        repository.save(aMsr(id, "Carol", "White", "carol@example.com", false));

        Optional<MedicalSalesRep> result =
                repository.findByEmail(new MedicalSalesRepEmail("carol@example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().id().value()).isEqualTo(id);
    }

    @Test
    void findByEmail_returnsEmpty_whenNotFound() {
        Optional<MedicalSalesRep> result =
                repository.findByEmail(new MedicalSalesRepEmail("nobody@example.com"));

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // searchAll
    // ---------------------------------------------------------------------------

    @Test
    void searchAll_returnsAllPersistedMsrs() {
        repository.save(aMsr(UUID.randomUUID().toString(), "Dave", "Brown", "dave@example.com", true));
        repository.save(aMsr(UUID.randomUUID().toString(), "Eve", "Green", "eve@example.com", false));

        List<MedicalSalesRep> all = repository.searchAll();

        assertThat(all).hasSize(2);
        assertThat(all)
                .extracting(msr -> msr.getEmail().value())
                .containsExactlyInAnyOrder("dave@example.com", "eve@example.com");
    }

    @Test
    void searchAll_returnsEmptyList_whenNoMsrsExist() {
        assertThat(repository.searchAll()).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // findByName
    // ---------------------------------------------------------------------------

    @Test
    void findByName_returnsOnlyMatchingMsr_whenBothNameAndSurnameProvided() {
        repository.save(aMsr(UUID.randomUUID().toString(), "Frank", "Miller", "frank@example.com", true));
        repository.save(aMsr(UUID.randomUUID().toString(), "Grace", "Miller", "grace@example.com", true));

        // "Frank" + "Miller" should match only the first record (prefix on both fields)
        List<MedicalSalesRep> results = repository.findByName(
                new MedicalSalesRepName("Frank"),
                new MedicalSalesRepName("Miller"),
                1, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName().value()).isEqualTo("Frank");
    }

    @Test
    void findByName_returnsFirstPage_whenResultsExceedPageSize() {
        for (int i = 0; i < 5; i++) {
            repository.save(aMsr(
                    UUID.randomUUID().toString(),
                    "Henry", "Taylor",
                    "henry" + i + "@example.com",
                    true));
        }

        List<MedicalSalesRep> page1 = repository.findByName(
                new MedicalSalesRepName("Henry"),
                new MedicalSalesRepName("Taylor"),
                1, 3);

        assertThat(page1).hasSize(3);
    }

    @Test
    void findByName_returnsLastPage_withRemainingResults() {
        for (int i = 0; i < 5; i++) {
            repository.save(aMsr(
                    UUID.randomUUID().toString(),
                    "Henry", "Taylor",
                    "henry" + i + "@example.com",
                    true));
        }

        List<MedicalSalesRep> page2 = repository.findByName(
                new MedicalSalesRepName("Henry"),
                new MedicalSalesRepName("Taylor"),
                2, 3);

        assertThat(page2).hasSize(2);
    }
}
