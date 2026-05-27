package com.das.cleanddd.domain.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot<TEvent> {
    private List<TEvent> domainEvents = new ArrayList<>();

    public final List<TEvent> pullDomainEvents() {
        List<TEvent> events = domainEvents;

        domainEvents = Collections.emptyList();

        return events;
    }

    protected final void record(TEvent event) {
        domainEvents.add(event);
    }
}