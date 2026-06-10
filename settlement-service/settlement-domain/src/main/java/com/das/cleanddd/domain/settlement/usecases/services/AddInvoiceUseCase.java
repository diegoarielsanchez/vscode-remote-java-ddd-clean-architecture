package com.das.cleanddd.domain.settlement.usecases.services;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceNumber;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.AddInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.InvoiceOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Use case that adds a new invoice to an existing settlement and atomically
 * attaches its digital file.
 *
 * <p>The operation follows a strict three-step sequence to keep physical storage
 * and database state consistent:</p>
 * <ol>
 *   <li>Add the invoice to the settlement aggregate (in memory).</li>
 *   <li>Persist the physical file via {@link IInvoiceFileStorage#store}.</li>
 *   <li>Attach the file to the invoice in the domain model and persist the
 *       settlement to the database via {@link ISettlementRepository#save}.
 *       If the DB write fails the physical file is deleted as a rollback.</li>
 * </ol>
 */
public final class AddInvoiceUseCase implements UseCase<AddInvoiceInputDTO, InvoiceOutputDTO> {

    private final ISettlementRepository repository;
    private final IInvoiceFileStorage fileStorage;
    private final SettlementMapper mapper;

    public AddInvoiceUseCase(ISettlementRepository repository,
                             IInvoiceFileStorage fileStorage,
                             SettlementMapper mapper) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.mapper = mapper;
    }

    @Override
    public InvoiceOutputDTO execute(AddInvoiceInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null.");
        }

        // Load settlement
        Settlement settlement = repository.findById(new SettlementId(input.settlementId()))
                .orElseThrow(() -> new DomainException("Settlement not found: " + input.settlementId()));

        // Step 1 — add invoice to domain model (in memory, not yet persisted)
        InvoiceNumber invoiceNumber;
        try {
            invoiceNumber = new InvoiceNumber(input.invoiceNumber());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        Invoice newInvoice;
        try {
            newInvoice = settlement.addInvoice(
                    invoiceNumber,
                    input.issueDate(),
                    input.dueDate(),
                    input.amount());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        // Build InvoiceFile value object
        InvoiceFile file;
        try {
            file = new InvoiceFile(input.fileName(), input.contentType(), input.content());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        // Step 2 — persist physical file
        fileStorage.store(newInvoice.invoiceId(), file);

        // Step 3 — attach file to invoice in domain model and persist to database.
        // On any failure the physical file is deleted to keep storage consistent.
        try {
            settlement.attachFileToInvoice(newInvoice.invoiceId(), file);
        } catch (IllegalArgumentException e) {
            fileStorage.delete(newInvoice.invoiceId(), file.fileName());  // rollback
            throw new DomainException(e.getMessage());
        }

        try {
            repository.save(settlement);
        } catch (RuntimeException e) {
            fileStorage.delete(newInvoice.invoiceId(), file.fileName());  // rollback
            throw new DomainException("Failed to persist invoice: " + e.getMessage());
        }

        return mapper.invoiceOutputFromInvoiceId(settlement, newInvoice.invoiceId());
    }
}
