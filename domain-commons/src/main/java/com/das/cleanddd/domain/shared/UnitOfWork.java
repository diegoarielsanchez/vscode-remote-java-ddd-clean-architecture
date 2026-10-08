package com.das.cleanddd.domain.shared;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Transaction boundary port. Use cases wrap "change the aggregate + record its events" in one
 * unit of work so both commit or roll back together (transactional outbox). Infrastructure
 * implements it (e.g. with Spring's TransactionTemplate); the domain stays framework-free.
 */
public interface UnitOfWork {

    <T> T execute(Work<T> work) throws DomainException;

    default void run(VoidWork work) throws DomainException {
        execute(() -> {
            work.run();
            return null;
        });
    }

    @FunctionalInterface
    interface Work<T> {
        T run() throws DomainException;
    }

    @FunctionalInterface
    interface VoidWork {
        void run() throws DomainException;
    }

    /** Runs the work directly, without a transaction: for unit tests and non-transactional contexts. */
    static UnitOfWork immediate() {
        return new UnitOfWork() {
            @Override
            public <T> T execute(Work<T> work) throws DomainException {
                return work.run();
            }
        };
    }
}
