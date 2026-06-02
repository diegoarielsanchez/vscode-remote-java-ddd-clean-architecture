package com.das.cleanddd.domain.settlement.usecases.services;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.InvoiceOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.UploadInvoiceFileInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public final class UploadInvoiceFileUseCase implements UseCase<UploadInvoiceFileInputDTO, InvoiceOutputDTO> {

    private final ISettlementRepository repository;
    private final IInvoiceFileStorage fileStorage;
    private final SettlementMapper mapper;

    public UploadInvoiceFileUseCase(ISettlementRepository repository,
                                    IInvoiceFileStorage fileStorage,
                                    SettlementMapper mapper) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.mapper = mapper;
    }

    @Override
    public InvoiceOutputDTO execute(UploadInvoiceFileInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null.");
        }

        Settlement settlement = repository.findById(new SettlementId(input.settlementId()))
                .orElseThrow(() -> new DomainException("Settlement not found: " + input.settlementId()));

        InvoiceId invoiceId = new InvoiceId(input.invoiceId());

        try {
            InvoiceFile file = new InvoiceFile(input.fileName(), input.contentType(), input.content());
            fileStorage.store(invoiceId, file);
            settlement.attachFileToInvoice(invoiceId, file);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }

        repository.save(settlement);

        return mapper.invoiceOutputFromInvoiceId(settlement, invoiceId);
    }
}
