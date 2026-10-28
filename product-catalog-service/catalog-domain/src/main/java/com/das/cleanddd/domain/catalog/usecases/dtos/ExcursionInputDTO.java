package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Readings for one lot from a data logger or monitoring system over {@code observedFrom}–{@code observedTo}.
 * {@code source} identifies the logger, fridge or shipment (required). Temperatures are in °C with at most one
 * decimal; give at least one reading. {@code note} is optional, at most 500 characters.
 */
public record ExcursionInputDTO(
    String productId,
    String batchNumber,
    String source,
    Instant observedFrom,
    Instant observedTo,
    BigDecimal minTemperatureC,
    BigDecimal maxTemperatureC,
    Integer maxHumidityPercent,
    Boolean lightExposed,
    String note
) {}
