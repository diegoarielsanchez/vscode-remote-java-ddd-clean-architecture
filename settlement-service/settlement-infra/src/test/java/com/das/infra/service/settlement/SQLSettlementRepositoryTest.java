package com.das.infra.service.settlement;

import com.das.cleanddd.domain.settlement.entities.DueDate;
import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.Invoice.InvoiceStatus;
import com.das.cleanddd.domain.settlement.entities.InvoiceAmount;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.cleanddd.domain.settlement.entities.InvoiceNumber;
import com.das.cleanddd.domain.settlement.entities.IssueDate;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.Settlement.SettlementStatus;
import com.das.cleanddd.domain.settlement.entities.SettlementDate;
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
        return aSettlement(id, description, msrId, List.of());
    }

    private Settlement aSettlement(String id, String description, String msrId, List<Invoice> invoices)
            throws BusinessValidationException {
        return new Settlement(
                new SettlementId(id),
                description,
                new SettlementDate(LocalDate.now()),
                SettlementStatus.OPEN,
                invoices,
                new MedicalSalesRepId(msrId));
    }

    private Invoice anInvoice(String id, String number) throws BusinessValidationException {
        return new Invoice(
                new InvoiceId(id),
                new InvoiceNumber(number),
                new IssueDate(LocalDate.now()),
                new DueDate(LocalDate.now().plusDays(30)),
                new InvoiceAmount(BigDecimal.valueOf(500)),
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
                new SettlementDate(LocalDate.now()),
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

    // ── invoice mapping ───────────────────────────────────────────────────────
    // The adapter maps the Settlement aggregate's invoices onto InvoiceEntity
    // children (@OneToMany, cascade ALL, orphanRemoval). Every test above builds
    // settlements with an empty invoice list, so these cover that mapping.

    @Test
    void save_cascadesInvoicesToChildRows() throws BusinessValidationException {
        String id    = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();

        repository.save(aSettlement(id, "With invoices", msrId, List.of(
                anInvoice(UUID.randomUUID().toString(), "A000100000001"),
                anInvoice(UUID.randomUUID().toString(), "A000100000002"))));

        SettlementEntity stored = jpaRepository.findById(id).orElseThrow();
        assertThat(stored.getInvoices())
                .extracting(InvoiceEntity::getInvoiceNumber)
                .containsExactlyInAnyOrder("A000100000001", "A000100000002");
    }

    @Test
    void findById_roundTripsInvoiceFields() throws BusinessValidationException {
        String id        = UUID.randomUUID().toString();
        String msrId     = UUID.randomUUID().toString();
        String invoiceId = UUID.randomUUID().toString();
        repository.save(aSettlement(id, "Round trip", msrId,
                List.of(anInvoice(invoiceId, "A000100000042"))));

        Settlement found = repository.findById(new SettlementId(id)).orElseThrow();

        assertThat(found.invoices()).hasSize(1);
        Invoice invoice = found.invoices().get(0);
        assertThat(invoice.invoiceId().value()).isEqualTo(invoiceId);
        assertThat(invoice.invoiceNumber().value()).isEqualTo("A000100000042");
        assertThat(invoice.amount().value()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.issueDate().value()).isEqualTo(LocalDate.now());
        assertThat(invoice.dueDate().value()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void findById_leavesInvoiceFileNull_whenNoFileWasAttached() throws BusinessValidationException {
        String id    = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();
        repository.save(aSettlement(id, "No file", msrId,
                List.of(anInvoice(UUID.randomUUID().toString(), "A000100000003"))));

        Settlement found = repository.findById(new SettlementId(id)).orElseThrow();

        // The adapter must not call the file storage port when the stored row has
        // no file name / content type / hash.
        assertThat(found.invoices().get(0).invoiceFile()).isNull();
    }

    @Test
    void save_removesOrphanedInvoices_whenResavedWithoutThem() throws BusinessValidationException {
        String id    = UUID.randomUUID().toString();
        String msrId = UUID.randomUUID().toString();
        repository.save(aSettlement(id, "Initial", msrId, List.of(
                anInvoice(UUID.randomUUID().toString(), "A000100000001"),
                anInvoice(UUID.randomUUID().toString(), "A000100000002"))));

        repository.save(aSettlement(id, "Initial", msrId, List.of()));

        SettlementEntity stored = jpaRepository.findById(id).orElseThrow();
        assertThat(stored.getInvoices()).isEmpty();
    }
}
