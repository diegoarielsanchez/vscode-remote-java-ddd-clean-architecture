package com.das.cleanddd.domain.healthcareprof.usecases.services;

import com.das.cleanddd.domain.healthcareprof.entities.*;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.CreateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateHealthCareProfUseCase")
class CreateHealthCareProfUseCaseTest {

    @Mock private IHealthCareProfRepository repository;
    @Mock private IHcpEventPublisher publisher;

    private HealthCareProfFactory factory;
    private HealthCareProfMapper mapper;
    private CreateHealthCareProfUseCase useCase;

    @BeforeEach
    void setUp() {
        factory = new HealthCareProfFactory();
        mapper  = new HealthCareProfMapper();
        useCase = new CreateHealthCareProfUseCase(repository, factory, mapper, publisher);
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("should create and persist a new HealthCareProf")
        void shouldCreateAndPersist() throws DomainException {
            when(repository.findByEmail(any(HealthCareProfEmail.class)))
                    .thenReturn(Optional.empty());

            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", "Smith", "john.smith@hospital.com", List.of("CARD"));

            HealthCareProfOutputDTO output = useCase.execute(input);

            assertNotNull(output.id());
            assertEquals("John",   output.name());
            assertEquals("Smith",  output.surname());
            assertEquals("john.smith@hospital.com", output.email());
            assertFalse(output.active()); // newly created → inactive
            assertTrue(output.specialties().contains("Cardiology"));

            verify(repository, times(1)).save(any(HealthCareProf.class));
            verify(publisher,  times(1)).publish(any());
        }

        @Test
        @DisplayName("should accept multiple specialties")
        void shouldAcceptMultipleSpecialties() throws DomainException {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());

            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "Ana", "Garcia", "ana@clinic.com", List.of("CARD", "NEUR", "PED"));

            HealthCareProfOutputDTO output = useCase.execute(input);
            assertEquals(3, output.specialties().size());
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
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    null, "Smith", "test@hospital.com", List.of("CARD"));
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when name is empty")
        void shouldThrowWhenNameEmpty() {
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "", "Smith", "test@hospital.com", List.of("CARD"));
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when surname is null")
        void shouldThrowWhenSurnameNull() {
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", null, "test@hospital.com", List.of("CARD"));
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when email is null")
        void shouldThrowWhenEmailNull() {
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", "Smith", null, List.of("CARD"));
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when specialties list is null")
        void shouldThrowWhenSpecialtiesNull() {
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", "Smith", "test@hospital.com", null);
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }

        @Test
        @DisplayName("should throw DomainException when specialties list is empty")
        void shouldThrowWhenSpecialtiesEmpty() {
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", "Smith", "test@hospital.com", List.of());
            assertThrows(DomainException.class, () -> useCase.execute(input));
        }
    }

    // ── business rules ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Business rules")
    class BusinessRules {

        @Test
        @DisplayName("should throw DomainException when email already exists")
        void shouldThrowWhenEmailAlreadyExists() {
            HealthCareProf existing = HealthCareProf.create(
                    new HealthCareProfName("Other"),
                    new HealthCareProfName("Person"),
                    new HealthCareProfEmail("john.smith@hospital.com"),
                    List.of(new Specialty("CARD", "Cardiology")));
            when(repository.findByEmail(any(HealthCareProfEmail.class)))
                    .thenReturn(Optional.of(existing));

            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", "Smith", "john.smith@hospital.com", List.of("CARD"));

            DomainException ex = assertThrows(DomainException.class, () -> useCase.execute(input));
            assertTrue(ex.getMessage().toLowerCase().contains("email") ||
                       ex.getMessage().toLowerCase().contains("health care professional"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should throw DomainException for an invalid specialty code")
        void shouldThrowForInvalidSpecialtyCode() {
            // findByEmail is not reached because specialty validation fails first
            CreateHealthCareProfInputDTO input = new CreateHealthCareProfInputDTO(
                    "John", "Smith", "john@hospital.com", List.of("INVALID_CODE"));

            assertThrows(DomainException.class, () -> useCase.execute(input));
            verify(repository, never()).save(any());
        }
    }
}
