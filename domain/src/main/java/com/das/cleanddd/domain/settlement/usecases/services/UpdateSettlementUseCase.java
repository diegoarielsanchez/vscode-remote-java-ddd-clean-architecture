package com.das.cleanddd.domain.settlement.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.UpdateSettlementInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class UpdateSettlementUseCase implements UseCase<UpdateSettlementInputDTO, SettlementOutputDTO> {

    @Autowired
    private final ISettlementRepository repository;
    @Autowired
    private final SettlementMapper mapper;

    public UpdateSettlementUseCase(ISettlementRepository repository, SettlementMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SettlementOutputDTO execute(UpdateSettlementInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null.");
        }
        if (inputDTO.id() == null || inputDTO.id().isBlank()) {
            throw new DomainException("Settlement id is required.");
        }
        if (inputDTO.description() == null || inputDTO.description().isBlank()) {
            throw new DomainException("Settlement description is required.");
        }
        if (inputDTO.settlementDate() == null) {
            throw new DomainException("Settlement date is required.");
        }

        try {
            SettlementId settlementId = new SettlementId(inputDTO.id());
            Optional<Settlement> existing = repository.findById(settlementId);
            if (existing.isEmpty()) {
                throw new DomainException("Settlement not found.");
            }

            Settlement updated = new Settlement(
                    settlementId,
                    inputDTO.description(),
                    inputDTO.settlementDate(),
                    existing.get().status(),
                    null);

            if (inputDTO.invoices() != null) {
                for (CreateInvoiceInputDTO invoiceDTO : inputDTO.invoices()) {
                    Invoice invoice = Invoice.create(
                            invoiceDTO.invoiceNumber(),
                            invoiceDTO.issueDate(),
                            invoiceDTO.dueDate(),
                            invoiceDTO.amount());
                    updated.addInvoice(invoice);
                }
            }

            repository.save(updated);
            return mapper.outputFromEntity(updated);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
