package com.das.inframySQL.service.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.cleanddd.domain.settlement.entities.InvoiceNumber;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.Settlement.SettlementStatus;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.settlement.entities.Invoice.InvoiceStatus;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Primary
@Service
public final class MySQLSettlementRepository implements ISettlementRepository {

    @Autowired
    private SettlementJpaRepository jpaRepository;

    @Override
    public void save(Settlement settlement) {
        SettlementEntity entity = toEntity(settlement);
        if (entity != null) {
            jpaRepository.save(entity);
        }
    }

    @Override
    public Optional<Settlement> findById(SettlementId id) {
        if (id == null || id.value() == null) {
            return Optional.empty();
        }
        String idValue = id.value();
        if (idValue == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(idValue)
                .map(this::toDomain);
    }

    @Override
    public List<Settlement> searchAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Settlement> matching(Criteria criteria) {
        return null;
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private Settlement toDomain(SettlementEntity entity) {
        List<Invoice> invoices = new ArrayList<>();
        if (entity.getInvoices() != null) {
            for (InvoiceEntity ie : entity.getInvoices()) {
                try {
                    invoices.add(invoiceToDomain(ie));
                } catch (BusinessValidationException ignored) {
                    // skip malformed rows
                }
            }
        }
        try {
            return new Settlement(
                    new SettlementId(entity.getId()),
                    entity.getDescription(),
                    entity.getSettlementDate(),
                    SettlementStatus.valueOf(entity.getStatus()),
                    invoices);
        } catch (BusinessValidationException e) {
            throw new IllegalStateException("Cannot reconstruct Settlement from DB row id=" + entity.getId(), e);
        }
    }

    private Invoice invoiceToDomain(InvoiceEntity ie) throws BusinessValidationException {
        return new Invoice(
                new InvoiceId(ie.getId()),
                new InvoiceNumber(ie.getInvoiceNumber()),
                ie.getIssueDate(),
                ie.getDueDate(),
                ie.getAmount(),
                InvoiceStatus.valueOf(ie.getStatus()));
    }

    private SettlementEntity toEntity(Settlement domain) {
        SettlementEntity entity = new SettlementEntity();
        entity.setId(domain.settlementId().value());
        entity.setDescription(domain.description());
        entity.setSettlementDate(domain.settlementDate());
        entity.setStatus(domain.status().name());

        List<InvoiceEntity> invoiceEntities = domain.invoices().stream()
                .map(inv -> invoiceToEntity(inv, entity))
                .collect(Collectors.toList());
        entity.setInvoices(invoiceEntities);

        return entity;
    }

    private InvoiceEntity invoiceToEntity(Invoice invoice, SettlementEntity parent) {
        InvoiceEntity ie = new InvoiceEntity();
        ie.setId(invoice.invoiceId().value());
        ie.setInvoiceNumber(invoice.invoiceNumber().value());
        ie.setIssueDate(invoice.issueDate());
        ie.setDueDate(invoice.dueDate());
        ie.setAmount(invoice.amount());
        ie.setStatus(invoice.status().name());
        ie.setSettlement(parent);
        return ie;
    }
}
