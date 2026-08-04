package com.das.infra.service.settlement;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

/**
 * Test-only configuration that provides a mock {@link IInvoiceFileStorage} bean
 * for the {@code @DataJpaTest} slice used in {@link SQLSettlementRepositoryTest}.
 * This avoids bringing in the real {@link LocalDiskInvoiceFileStorage} (which
 * requires a {@code invoice.file.storage.path} property and a real file system).
 */
@TestConfiguration
class MockInvoiceFileStorageConfig {

    @Bean
    IInvoiceFileStorage invoiceFileStorage() {
        return mock(IInvoiceFileStorage.class);
    }
}
