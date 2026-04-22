package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.Settlement;

@Service
public class SettlementMapper {

    public SettlementOutputDTO outputFromEntity(Settlement settlement) {
        List<InvoiceOutputDTO> invoiceDTOs = settlement.invoices().stream()
                .map(this::invoiceOutputFromEntity)
                .toList();
        return new SettlementOutputDTO(
                settlement.settlementId().value(),
                settlement.description(),
                settlement.settlementDate(),
                settlement.status().name(),
                settlement.totalAmount(),
                invoiceDTOs);
    }

    public List<SettlementOutputDTO> outputFromEntityList(List<Settlement> settlements) {
        return settlements.stream().map(this::outputFromEntity).toList();
    }

    private InvoiceOutputDTO invoiceOutputFromEntity(Invoice invoice) {
        return new InvoiceOutputDTO(
                invoice.invoiceId().value(),
                invoice.invoiceNumber(),
                invoice.issueDate(),
                invoice.dueDate(),
                invoice.amount(),
                invoice.status().name());
    }
}
