package com.das.infra.service.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * {@link UnitOfWork} adapter backed by Spring's transaction manager. Any exception — including
 * the domain's checked {@link DomainException} — rolls the transaction back, so the aggregate
 * change and its outbox rows are committed together or not at all.
 */
@Component
public class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    public SpringUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Work<T> work) throws DomainException {
        try {
            return transactionTemplate.execute(status -> {
                try {
                    return work.run();
                } catch (DomainException e) {
                    throw new DomainExceptionCarrier(e); // runtime → TransactionTemplate rolls back
                }
            });
        } catch (DomainExceptionCarrier carrier) {
            throw carrier.domainException;
        }
    }

    private static final class DomainExceptionCarrier extends RuntimeException {
        private final transient DomainException domainException;

        private DomainExceptionCarrier(DomainException domainException) {
            super(domainException.getMessage(), domainException, false, false);
            this.domainException = domainException;
        }
    }
}
