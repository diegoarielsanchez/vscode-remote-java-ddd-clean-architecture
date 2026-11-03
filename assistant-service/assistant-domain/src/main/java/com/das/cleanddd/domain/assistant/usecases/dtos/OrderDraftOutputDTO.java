package com.das.cleanddd.domain.assistant.usecases.dtos;

import java.time.Instant;
import java.util.List;

/** @param orderId the order the draft became, once confirmed */
public record OrderDraftOutputDTO(
    String id,
    String status,
    List<Line> lines,
    List<Check> checks,
    boolean confirmable,
    String contextHcpId,
    String rationale,
    Instant createdAt,
    Instant expiresAt,
    String orderId
) {
    public record Line(String productId, String gtin, String name, int quantity) {}

    public record Check(String rule, String productId, String outcome, String message) {}
}
