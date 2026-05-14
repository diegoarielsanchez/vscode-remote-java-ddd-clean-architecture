package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.settlement.entities.Settlement;

@Service
public class SettlementMapper {

    public SettlementOutputDTO outputFromEntity(Settlement settlement) {
        List<InvoiceOutputDTO> invoiceDTOs = settlement.invoices().stream()
                .map(this::invoiceOutputFromEntity)
                .toList();
        MedicalSalesRepId msrId = settlement.medicalSalesRepId();
        return new SettlementOutputDTO(
                settlement.settlementId().value(),
                settlement.description(),
                settlement.settlementDate().value(),
                settlement.status().name(),
                settlement.totalAmount(),
                invoiceDTOs,
                msrId != null ? msrId.value() : null);
    }

    public List<SettlementOutputDTO> outputFromEntityList(List<Settlement> settlements) {
        return settlements.stream().map(this::outputFromEntity).toList();
    }

    private InvoiceOutputDTO invoiceOutputFromEntity(Invoice invoice) {
        return new InvoiceOutputDTO(
                invoice.invoiceId().value(),
                invoice.invoiceNumber().value(),
                invoice.issueDate().value(),
                invoice.dueDate() != null ? invoice.dueDate().value() : null,
                invoice.amount(),
                invoice.status().name());
    }
}
