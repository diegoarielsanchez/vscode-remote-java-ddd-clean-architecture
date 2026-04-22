package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SettlementOutputDTO(
    String id,
    String description,
    LocalDate settlementDate,
    String status,
    BigDecimal totalAmount,
    List<InvoiceOutputDTO> invoices
) {}
