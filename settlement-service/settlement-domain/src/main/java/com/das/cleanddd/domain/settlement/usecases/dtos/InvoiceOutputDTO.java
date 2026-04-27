package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceOutputDTO(
    String id,
    String invoiceNumber,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal amount,
    String status
) {}
