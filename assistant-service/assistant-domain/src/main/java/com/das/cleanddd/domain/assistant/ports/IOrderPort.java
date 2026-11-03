package com.das.cleanddd.domain.assistant.ports;

import java.util.List;

import com.das.cleanddd.domain.assistant.entities.DraftLine;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Places an order with order-service, as the calling rep (order-service takes the MSR from the rep's token).
 *
 * <p>Only {@code ConfirmDraftUseCase} uses this port, and only after the rep confirmed. No assistant tool can
 * reach it.
 */
public interface IOrderPort {

    /** @return the id of the accepted order */
    String placeOrder(List<DraftLine> lines) throws DomainException;
}
