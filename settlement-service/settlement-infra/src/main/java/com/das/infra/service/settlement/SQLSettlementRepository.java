package com.das.infra.service.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.DueDate;
import com.das.cleanddd.domain.settlement.entities.IssueDate;
import com.das.cleanddd.domain.settlement.entities.Invoice;
import com.das.cleanddd.domain.settlement.entities.Invoice.InvoiceStatus;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.cleanddd.domain.settlement.entities.InvoiceNumber;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.entities.Settlement.SettlementStatus;
import com.das.cleanddd.domain.settlement.entities.SettlementDate;
import com.das.cleanddd.domain.settlement.entities.SettlementId;
import com.das.cleanddd.domain.shared.criteria.Criteria;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Primary
@Service
public final class SQLSettlementRepository implements ISettlementRepository {

    @Autowired
    private SettlementJpaRepository jpaRepository;

    @Autowired
    private IInvoiceFileStorage fileStorage;

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
    public List<Settlement> searchAll(int page, int pageSize) {
        return jpaRepository.findAll(PageRequest.of(page - 1, pageSize)).stream()
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
            MedicalSalesRepId msrId = entity.getMedicalSalesRepId() != null
                    ? new MedicalSalesRepId(entity.getMedicalSalesRepId())
                    : null;
            return new Settlement(
                    new SettlementId(entity.getId()),
                    entity.getDescription(),
                    new SettlementDate(entity.getSettlementDate()),
                    SettlementStatus.valueOf(entity.getStatus()),
                    invoices,
                    msrId);
        } catch (BusinessValidationException e) {
            throw new IllegalStateException("Cannot reconstruct Settlement from DB row id=" + entity.getId(), e);
        }
    }

    private Invoice invoiceToDomain(InvoiceEntity ie) throws BusinessValidationException {
        InvoiceFile invoiceFile = null;
        if (ie.getInvoiceFileFileName() != null && ie.getInvoiceFileContentType() != null
                && ie.getInvoiceFileHash() != null) {
            InvoiceId invoiceId = new InvoiceId(ie.getId());
            java.util.Optional<byte[]> content =
                    fileStorage.loadContent(invoiceId, ie.getInvoiceFileFileName(), ie.getInvoiceFileHash());
            if (content.isPresent()) {
                invoiceFile = new InvoiceFile(
                        ie.getInvoiceFileFileName(),
                        ie.getInvoiceFileContentType(),
                        content.get());
            }
        }
        return new Invoice(
                new InvoiceId(ie.getId()),
                new InvoiceNumber(ie.getInvoiceNumber()),
                new IssueDate(ie.getIssueDate()),
                ie.getDueDate() != null ? new DueDate(ie.getDueDate()) : null,
                ie.getAmount(),
                InvoiceStatus.valueOf(ie.getStatus()),
                invoiceFile);
    }

    private SettlementEntity toEntity(Settlement domain) {
        SettlementEntity entity = new SettlementEntity();
        entity.setId(domain.settlementId().value());
        entity.setDescription(domain.description());
        entity.setSettlementDate(domain.settlementDate().value());
        entity.setStatus(domain.status().name());
        entity.setMedicalSalesRepId(domain.medicalSalesRepId() != null ? domain.medicalSalesRepId().value() : null);

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
        ie.setIssueDate(invoice.issueDate().value());
        ie.setDueDate(invoice.dueDate() != null ? invoice.dueDate().value() : null);
        ie.setAmount(invoice.amount());
        ie.setStatus(invoice.status().name());
        ie.setSettlement(parent);

        InvoiceFile file = invoice.invoiceFile();
        if (file != null) {
            fileStorage.store(invoice.invoiceId(), file);
            ie.setInvoiceFileFileName(file.fileName());
            ie.setInvoiceFileContentType(file.contentType());
            ie.setInvoiceFileSizeInBytes(file.sizeInBytes());
            ie.setInvoiceFileHash(file.sha256Hash());
        }

        return ie;
    }
}
