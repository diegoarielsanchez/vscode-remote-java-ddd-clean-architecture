package com.das.cleanddd.domain.assistant.ports;

import java.util.Optional;

/**
 * Reads from visit-service, made as the calling rep. visit-service's {@code GET /api/v1/visit/{id}} does not check
 * who the visit belongs to, so callers must compare {@link VisitView#medicalSalesRepId()} with the rep themselves.
 */
public interface IVisitPort {

    Optional<VisitView> findVisit(String visitId);
}
