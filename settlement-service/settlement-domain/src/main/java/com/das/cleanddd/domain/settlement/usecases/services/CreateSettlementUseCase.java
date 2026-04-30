package com.das.cleanddd.domain.settlement.usecases.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.InvoiceNumber;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class CreateSettlementUseCase implements UseCase<CreateSettlementInputDTO, SettlementOutputDTO> {

    @Autowired
    private final ISettlementRepository repository;
    @Autowired
    private final SettlementMapper mapper;

    public CreateSettlementUseCase(ISettlementRepository repository, SettlementMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SettlementOutputDTO execute(CreateSettlementInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null.");
        }
        if (inputDTO.description() == null || inputDTO.description().isBlank()) {
            throw new DomainException("Settlement description is required.");
        }
        if (inputDTO.settlementDate() == null) {
            throw new DomainException("Settlement date is required.");
        }

        try {
            MedicalSalesRepId msrId = inputDTO.medicalSalesRepId() != null && !inputDTO.medicalSalesRepId().isBlank()
                    ? new MedicalSalesRepId(inputDTO.medicalSalesRepId())
                    : null;
            Settlement settlement = Settlement.create(inputDTO.description(), inputDTO.settlementDate(), msrId);

            if (inputDTO.invoices() != null) {
                for (CreateInvoiceInputDTO invoiceDTO : inputDTO.invoices()) {
                    settlement.addInvoice(
                            new InvoiceNumber(invoiceDTO.invoiceNumber()),
                            invoiceDTO.issueDate(),
                            invoiceDTO.dueDate(),
                            invoiceDTO.amount());
                }
            }

            repository.save(settlement);
            return mapper.outputFromEntity(settlement);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
