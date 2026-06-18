package com.das.infra.service.settlement;

import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.Invoice.InvoiceStatus;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.cleanddd.domain.settlement.entities.InvoiceNumber;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.Settlement.SettlementStatus;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link SQLSettlementRepository}.
 *
 * Uses {@code @DataJpaTest} to spin up an in-memory H2 database and auto-configure
 * JPA. The repository adapter is imported explicitly because it is a {@code @Service},
 * not a {@code @Repository}. {@link LocalDiskInvoiceFileStorage} is not used here —
 * a mock is injected to keep these tests focused on the JPA mapping.
 */
@DataJpaTest
@Import({SQLSettlementRepository.class, MockInvoiceFileStorageConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SQLSettlementRepositoryTest {

    @Autowired
    private SQLSettlementRepository repository;

    @Autowired
    private SettlementJpaRepository jpaRepository;

    @AfterEach
    void cleanUp() {
        jpaRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Settlement aSettlement(String id, String description, String msrId)
            throws BusinessValidationException {
        return new Settlement(
                new SettlementId(id),
                description,
                LocalDate.now(),
                SettlementStatus.OPEN,
                List.of(),
                new MedicalSalesRepId(msrId));
    }

    private Invoice anInvoice(String id, String number) throws BusinessValidationException {
        return new Invoice(
                new InvoiceId(id),
                new InvoiceNumber(number),
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                BigDecimal.valueOf(500),
                InvoiceStatus.DRAFT);
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_persistsNewSettlement() throws BusinessValidationException {
        String id    = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();

        repository.save(aSettlement(id, "Q1 Settlement", msrId));

        Optional<SettlementEntity> stored = jpaRepository.findById(id);
        assertThat(stored).isPresent();
        assertThat(stored.get().getDescription()).isEqualTo("Q1 Settlement");
        assertThat(stored.get().getStatus()).isEqualTo("OPEN");
        assertThat(stored.get().getMedicalSalesRepId()).isEqualTo(msrId);
    }

    @Test
    void save_updatesExistingRecord_whenSameIdSavedAgain() throws BusinessValidationException {
        String id    = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();
        repository.save(aSettlement(id, "Original", msrId));

        Settlement updated = new Settlement(
                new SettlementId(id),
                "Updated",
                LocalDate.now(),
                SettlementStatus.CLOSED,
                List.of(),
                new MedicalSalesRepId(msrId));
        repository.save(updated);

        SettlementEntity stored = jpaRepository.findById(id).orElseThrow();
        assertThat(stored.getDescription()).isEqualTo("Updated");
        assertThat(stored.getStatus()).isEqualTo("CLOSED");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsPersistedSettlement() throws BusinessValidationException {
        String id    = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();
        repository.save(aSettlement(id, "Find me", msrId));

        Optional<Settlement> result = repository.findById(new SettlementId(id));

        assertThat(result).isPresent();
        assertThat(result.get().settlementId().value()).isEqualTo(id);
        assertThat(result.get().description()).isEqualTo("Find me");
        assertThat(result.get().medicalSalesRepId().value()).isEqualTo(msrId);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        Optional<Settlement> result =
                repository.findById(new SettlementId(UUID.randomUUID().toString()));

        assertThat(result).isEmpty();
    }

    // ── searchAll ─────────────────────────────────────────────────────────────

    @Test
    void searchAll_returnsAllPersistedSettlements() throws BusinessValidationException {
        repository.save(aSettlement(UUID.randomUUID().toString(), "S1", UUID.randomUUID().toString()));
        repository.save(aSettlement(UUID.randomUUID().toString(), "S2", UUID.randomUUID().toString()));

        List<Settlement> all = repository.searchAll();

        assertThat(all).hasSize(2);
        assertThat(all)
                .extracting(Settlement::description)
                .containsExactlyInAnyOrder("S1", "S2");
    }

    @Test
    void searchAll_returnsEmptyList_whenNoSettlementsExist() {
        assertThat(repository.searchAll()).isEmpty();
    }

    // ── searchAll (paged) ─────────────────────────────────────────────────────

    @Test
    void searchAll_paged_returnsFirstPage() throws BusinessValidationException {
        for (int i = 1; i <= 5; i++) {
            repository.save(aSettlement(
                    UUID.randomUUID().toString(),
                    "Settlement " + i,
                    UUID.randomUUID().toString()));
        }

        List<Settlement> page1 = repository.searchAll(1, 3);

        assertThat(page1).hasSize(3);
    }

    @Test
    void searchAll_paged_returnsRemainingOnSecondPage() throws BusinessValidationException {
        for (int i = 1; i <= 5; i++) {
            repository.save(aSettlement(
                    UUID.randomUUID().toString(),
                    "Settlement " + i,
                    UUID.randomUUID().toString()));
        }

        List<Settlement> page2 = repository.searchAll(2, 3);

        assertThat(page2).hasSize(2);
    }
}
