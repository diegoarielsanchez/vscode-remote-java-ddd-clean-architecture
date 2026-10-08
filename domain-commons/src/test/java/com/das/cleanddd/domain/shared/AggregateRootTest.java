package com.das.cleanddd.domain.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AggregateRootTest {

    /** Minimal immutable aggregate: each change returns a new instance, like Order. */
    private static final class Counter extends AggregateRoot<String> {
        static Counter create() {
            Counter c = new Counter();
            c.record("created");
            return c;
        }

        Counter increment() {
            Counter next = new Counter();
            next.carryOverEventsFrom(this);
            next.record("incremented");
            return next;
        }

        void touch() {
            record("touched");
        }
    }

    @Test
    void chainedTransitionsKeepEarlierEventsInOrder() {
        assertEquals(List.of("created", "incremented", "incremented"), Counter.create().increment().increment().pullDomainEvents());
    }

    @Test
    void anAggregateCanRecordAgainAfterItsEventsWerePulled() {
        Counter c = Counter.create();
        assertEquals(List.of("created"), c.pullDomainEvents());
        assertTrue(c.pullDomainEvents().isEmpty());

        c.touch();

        assertEquals(List.of("touched"), c.pullDomainEvents());
    }
}
