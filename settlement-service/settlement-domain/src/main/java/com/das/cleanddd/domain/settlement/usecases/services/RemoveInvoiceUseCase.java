package com.das.cleanddd.domain.settlement.usecases.services;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.RemoveInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Use case that removes an invoice (and its digital file, if any) from an
 * existing settlement.
 *
 * <p>The settlement must not be CLOSED. The operation follows a safe two-step
 * sequence:</p>
 * <ol>
 *   <li>Delete the physical file from storage (if the invoice has one attached).</li>
 *   <li>Remove the invoice from the settlement aggregate and persist the updated
 *       settlement to the database. If the DB write fails and a file was deleted,
 *       this is reported as a domain exception — the file is not restored because
 *       the domain model still reflects the file's absence.</li>
 * </ol>
 */
public final class RemoveInvoiceUseCase implements UseCase<RemoveInvoiceInputDTO, SettlementOutputDTO> {

    private final ISettlementRepository repository;
    private final IInvoiceFileStorage fileStorage;
    private final SettlementMapper mapper;

    public RemoveInvoiceUseCase(ISettlementRepository repository,
                                IInvoiceFileStorage fileStorage,
                                SettlementMapper mapper) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.mapper = mapper;
    }

    @Override
    public SettlementOutputDTO execute(RemoveInvoiceInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null.");
        }

        // Load settlement
        Settlement settlement = repository.findById(new SettlementId(input.settlementId()))
                .orElseThrow(() -> new DomainException("Settlement not found: " + input.settlementId()));

        // Find invoice by ID
        InvoiceId invoiceId = new InvoiceId(input.invoiceId());
        Invoice invoice = settlement.invoices().stream()
                .filter(i -> i.invoiceId().equals(invoiceId))
                .findFirst()
                .orElseThrow(() -> new DomainException("Invoice not found: " + input.invoiceId()));

        // Step 1 — delete physical file from storage (if present)
        InvoiceFile file = invoice.invoiceFile();
        if (file != null) {
            fileStorage.delete(invoiceId, file.fileName());
        }

        // Step 2 — remove invoice from domain model and persist
        try {
            settlement.removeInvoice(invoice);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        try {
            repository.save(settlement);
        } catch (RuntimeException e) {
            throw new DomainException("Failed to persist settlement after invoice removal: " + e.getMessage());
        }

        return mapper.outputFromEntity(settlement);
    }
}
