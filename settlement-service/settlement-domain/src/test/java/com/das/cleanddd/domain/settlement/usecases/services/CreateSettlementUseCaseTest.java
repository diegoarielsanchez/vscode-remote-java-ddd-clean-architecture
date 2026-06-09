package com.das.cleanddd.domain.settlement.usecases.services;

import com.das.cleanddd.domain.settlement.entities.IMedicalSalesRepPort;
import com.das.cleanddd.domain.settlement.entities.ISettlementRepository;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.settlement.entities.Settlement;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateInvoiceInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementMapper;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateSettlementUseCase")
class CreateSettlementUseCaseTest {

    @Mock private ISettlementRepository repository;
    @Mock private IMedicalSalesRepPort  medicalSalesRepPort;

    private SettlementMapper mapper;
    private CreateSettlementUseCase useCase;

    // Invoice issue date must be within the last 60 days
    private static final LocalDate VALID_ISSUE_DATE = LocalDate.now().minusDays(30);
    private static final LocalDate SETTLEMENT_DATE  = LocalDate.now();
    private static final String    MSR_ID           = "123e4567-e89b-12d3-a456-426614174000";

    @BeforeEach
    void setUp() {
        mapper  = new SettlementMapper();
        useCase = new CreateSettlementUseCase(repository, mapper, medicalSalesRepPort);
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should create and persist a settlement without invoices")
        void shouldCreateSettlementWithoutInvoices() throws DomainException {
            when(medicalSalesRepPort.existsAndIsActive(any(MedicalSalesRepId.class))).thenReturn(true);

            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "Monthly settlement", SETTLEMENT_DATE, null, MSR_ID);

            SettlementOutputDTO output = useCase.execute(input);

            assertNotNull(output.id());
            assertEquals("Monthly settlement", output.description());
            assertEquals(SETTLEMENT_DATE, output.settlementDate());
            assertEquals("OPEN", output.status());
            assertEquals(BigDecimal.ZERO, output.totalAmount());
            assertEquals(MSR_ID, output.medicalSalesRepId());

            verify(repository, times(1)).save(any(Settlement.class));
        }

        @Test
        @DisplayName("should create a settlement with invoices and persist it")
        void shouldCreateSettlementWithInvoices() throws DomainException {
            when(medicalSalesRepPort.existsAndIsActive(any())).thenReturn(true);

            CreateInvoiceInputDTO invoiceDTO = new CreateInvoiceInputDTO(
                    "A000100000001", VALID_ISSUE_DATE, null, new BigDecimal("750.00"));

            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "Q1 settlement", SETTLEMENT_DATE, List.of(invoiceDTO), MSR_ID);

            SettlementOutputDTO output = useCase.execute(input);

            assertEquals(1, output.invoices().size());
            assertEquals(new BigDecimal("750.00"), output.totalAmount());

            ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
            verify(repository).save(captor.capture());
            assertEquals(1, captor.getValue().invoices().size());
        }

        @Test
        @DisplayName("should create a settlement with multiple invoices and compute total")
        void shouldSumMultipleInvoices() throws DomainException {
            when(medicalSalesRepPort.existsAndIsActive(any())).thenReturn(true);

            List<CreateInvoiceInputDTO> invoices = List.of(
                    new CreateInvoiceInputDTO("A000100000001", VALID_ISSUE_DATE, null, new BigDecimal("300.00")),
                    new CreateInvoiceInputDTO("A000100000002", VALID_ISSUE_DATE, null, new BigDecimal("200.50")));

            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "Multi invoice", SETTLEMENT_DATE, invoices, MSR_ID);

            SettlementOutputDTO output = useCase.execute(input);
            assertEquals(new BigDecimal("500.50"), output.totalAmount());
        }
    }

    // ── input validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should throw DomainException when input is null")
        void shouldThrowWhenInputNull() {
            assertThrows(DomainException.class, () -> useCase.execute(null));
        }

        @Test
        @DisplayName("should throw DomainException when description is null")
        void shouldThrowWhenDescriptionNull() {
            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    null, SETTLEMENT_DATE, null, MSR_ID);
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when description is blank")
        void shouldThrowWhenDescriptionBlank() {
            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "   ", SETTLEMENT_DATE, null, MSR_ID);
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when settlement date is null")
        void shouldThrowWhenDateNull() {
            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "desc", null, null, MSR_ID);
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when medicalSalesRepId is null")
        void shouldThrowWhenMsrIdNull() {
            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "desc", SETTLEMENT_DATE, null, null);
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when medicalSalesRepId is blank")
        void shouldThrowWhenMsrIdBlank() {
            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "desc", SETTLEMENT_DATE, null, "  ");
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }
    }

    // ── business rules ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Business rules")
    class BusinessRules {

        @Test
        @DisplayName("should throw DomainException when MSR does not exist or is not active")
        void shouldThrowWhenMsrInactive() {
            when(medicalSalesRepPort.existsAndIsActive(any())).thenReturn(false);

            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "desc", SETTLEMENT_DATE, null, MSR_ID);

            DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
            assertTrue(ex.getMessage().toLowerCase().contains("medical sales rep"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DomainException when an invoice has an invalid number format")
        void shouldThrowWhenInvoiceNumberInvalid() {
            when(medicalSalesRepPort.existsAndIsActive(any())).thenReturn(true);

            CreateInvoiceInputDTO badInvoice = new CreateInvoiceInputDTO(
                    "INVALID", VALID_ISSUE_DATE, null, new BigDecimal("100.00"));

            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "desc", SETTLEMENT_DATE, List.of(badInvoice), MSR_ID);

            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DomainException when duplicate invoice numbers are provided")
        void shouldThrowWhenDuplicateInvoiceNumbers() {
            when(medicalSalesRepPort.existsAndIsActive(any())).thenReturn(true);

            List<CreateInvoiceInputDTO> invoices = List.of(
                    new CreateInvoiceInputDTO("A000100000001", VALID_ISSUE_DATE, null, new BigDecimal("100.00")),
                    new CreateInvoiceInputDTO("A000100000001", VALID_ISSUE_DATE, null, new BigDecimal("200.00")));

            CreateSettlementInputDTO input = new CreateSettlementInputDTO(
                    "desc", SETTLEMENT_DATE, invoices, MSR_ID);

            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should not persist when creation fails")
        void shouldNotPersistOnFailure() {
            assertThrows(DomainException.class,
                    () -> useCase.execute(new CreateSettlementInputDTO(null, SETTLEMENT_DATE, null, MSR_ID)));
            verify(repository, never()).save(any());
        }
    }
}
