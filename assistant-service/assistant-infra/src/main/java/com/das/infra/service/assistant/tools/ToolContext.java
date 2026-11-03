package com.das.infra.service.assistant.tools;

/**
 * Who a tool runs for. It comes from the rep's token and the running conversation, never from the model's tool
 * input: no tool takes a rep id as input, so the model cannot act for another rep (OWASP A01).
 */
public record ToolContext(String medicalSalesRepId, String conversationId) {}
