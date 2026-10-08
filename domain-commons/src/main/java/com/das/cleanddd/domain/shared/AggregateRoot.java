package com.das.cleanddd.domain.shared;

import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot<TEvent> {
    private List<TEvent> domainEvents = new ArrayList<>();

    public final List<TEvent> pullDomainEvents() {
        List<TEvent> events = domainEvents;

        domainEvents = new ArrayList<>(); // stays recordable after a pull

        return events;
    }

    protected final void record(TEvent event) {
        domainEvents.add(event);
    }

    /**
     * For immutable aggregates whose state changes return a new instance: keeps the events recorded
     * on the previous instance and not yet pulled, so chained transitions (e.g. create → submit)
     * don't lose events. Call it before recording the new instance's own event to keep their order.
     */
    protected final void carryOverEventsFrom(AggregateRoot<TEvent> previous) {
        domainEvents.addAll(0, previous.pullDomainEvents());
    }
}
