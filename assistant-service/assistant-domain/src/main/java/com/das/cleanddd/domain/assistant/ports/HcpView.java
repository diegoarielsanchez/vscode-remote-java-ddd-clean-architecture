package com.das.cleanddd.domain.assistant.ports;

import java.util.List;

/** An HCP as the assistant sees it: no email or other contact details, which drafting an order does not need. */
public record HcpView(String id, String name, String surname, List<String> specialties, boolean active) {}
