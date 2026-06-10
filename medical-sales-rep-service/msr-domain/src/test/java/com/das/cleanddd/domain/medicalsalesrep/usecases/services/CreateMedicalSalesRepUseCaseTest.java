package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import com.das.cleanddd.domain.medicalsalesrep.entities.*;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepMapper;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMedicalSalesRepUseCase")
class CreateMedicalSalesRepUseCaseTest {

    @Mock private IMedicalSalesRepRepository repository;
    @Mock private IMsrEventPublisher publisher;

    private MedicalSalesRepMapper mapper;
    private CreateMedicalSalesRepUseCase useCase;

    @BeforeEach
    void setUp() {
        new MedicalSalesRepFactory();
        mapper  = new MedicalSalesRepMapper();
        useCase = new CreateMedicalSalesRepUseCase(repository, mapper, publisher);
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should create and persist a new MedicalSalesRep")
        void shouldCreateAndPersist() throws DomainException {
            when(repository.findByEmail(any(MedicalSalesRepEmail.class)))
                    .thenReturn(Optional.empty());

            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", "Smith", "john.smith@pharma.com");

            MedicalSalesRepOutputDTO output = useCase.execute(input);

            assertNotNull(output.id());
            assertEquals("John",  output.name());
            assertEquals("Smith", output.surname());
            assertEquals("john.smith@pharma.com", output.email());
            assertFalse(output.active()); // newly created → inactive

            verify(repository, times(1)).save(any(MedicalSalesRep.class));
            verify(publisher,  times(1)).publish(any());
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
        @DisplayName("should throw DomainException when name is null")
        void shouldThrowWhenNameNull() {
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO(null, "Smith", "test@pharma.com");
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when name is empty")
        void shouldThrowWhenNameEmpty() {
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("", "Smith", "test@pharma.com");
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when surname is null")
        void shouldThrowWhenSurnameNull() {
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", null, "test@pharma.com");
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when surname is empty")
        void shouldThrowWhenSurnameEmpty() {
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", "", "test@pharma.com");
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when email is null")
        void shouldThrowWhenEmailNull() {
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", "Smith", null);
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when email is empty")
        void shouldThrowWhenEmailEmpty() {
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", "Smith", "");
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when email format is invalid")
        void shouldThrowWhenEmailInvalidFormat() {
            // MedicalSalesRepEmail validates format at construction; findByEmail is never reached
            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", "Smith", "not-an-email");
            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }
    }

    // ── business rules ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Business rules")
    class BusinessRules {

        @Test
        @DisplayName("should throw DomainException when email already exists")
        void shouldThrowWhenEmailAlreadyExists() {
            MedicalSalesRep existing = new MedicalSalesRep(
                    MedicalSalesRepId.random(),
                    new MedicalSalesRepName("Other"),
                    new MedicalSalesRepName("Person"),
                    new MedicalSalesRepEmail("john.smith@pharma.com"),
                    new MedicalSalesRepActive(true));

            when(repository.findByEmail(any(MedicalSalesRepEmail.class)))
                    .thenReturn(Optional.of(existing));

            CreateMedicalSalesRepInputDTO input =
                    new CreateMedicalSalesRepInputDTO("John", "Smith", "john.smith@pharma.com");

            DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
            assertTrue(ex.getMessage().toLowerCase().contains("email") ||
                       ex.getMessage().toLowerCase().contains("medical sales"));

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should not publish event when creation fails")
        void shouldNotPublishWhenCreationFails() {
            assertThrows(DomainException.class,
                    () -> useCase.execute(new CreateMedicalSalesRepInputDTO(null, "Smith", "test@pharma.com")));
            verify(publisher, never()).publish(any());
        }
    }
}
