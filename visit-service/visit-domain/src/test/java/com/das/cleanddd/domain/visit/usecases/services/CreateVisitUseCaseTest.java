package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.VisitFactory;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

@ExtendWith(MockitoExtension.class)
class CreateVisitUseCaseTest {

    private static final String    HCP_ID         = "123e4567-e89b-12d3-a456-426614174002";
    private static final String    MSR_ID         = "123e4567-e89b-12d3-a456-426614174003";
    private static final String    SITE_ID        = "123e4567-e89b-12d3-a456-426614174004";
    private static final LocalDate VALID_DATE     = LocalDate.now().minusDays(1);

    @Mock private IVisitRepository          visitRepository;
    @Mock private IHealthCareProfValidator  healthCareProfValidator;
    @Mock private IMedicalSalesRepValidator medicalSalesRepValidator;

    private CreateVisitUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateVisitUseCase(
            visitRepository,
            healthCareProfValidator,
            medicalSalesRepValidator,
            new VisitFactory(),
            new VisitMapper()
        );
    }

    // ------------------------------------------------------------------ //
    @Nested
    class HappyPath {

        @BeforeEach
        void setUpHappy() {
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(true);
            when(visitRepository.existsByVisitKey(any(), any(), any())).thenReturn(false);
        }

        @Test
        void shouldCreateVisitAndReturnOutputDTO() throws DomainException {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, "routine check", SITE_ID, MSR_ID
            );

            VisitOutputDTO output = useCase.execute(input);

            assertThat(output).isNotNull();
            assertThat(output.id()).isNotBlank();
            assertThat(output.healthCareProfId()).isEqualTo(HCP_ID);
            assertThat(output.medicalSalesRepId()).isEqualTo(MSR_ID);
            assertThat(output.visitSiteId()).isEqualTo(SITE_ID);
            assertThat(output.visitComments()).isEqualTo("routine check");
            verify(visitRepository).save(any());
        }

        @Test
        void shouldCreateVisitWithNullComments() throws DomainException {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID
            );

            VisitOutputDTO output = useCase.execute(input);

            assertThat(output.visitComments()).isNull();
            verify(visitRepository).save(any());
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class InputValidation {

        @Test
        void shouldThrowWhenInputDTOIsNull() {
            assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("null");
        }

        @Test
        void shouldThrowWhenVisitDateIsNull() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                null, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("date cannot be null");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsNull() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, null, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional id");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsBlank() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, "  ", null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional id");
        }

        @Test
        void shouldThrowWhenVisitSiteIdIsNull() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, null, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit site id");
        }

        @Test
        void shouldThrowWhenVisitSiteIdIsBlank() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, "", MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit site id");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsNull() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, SITE_ID, null
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative id");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsBlank() {
            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, SITE_ID, "   "
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative id");
        }
    }

    // ------------------------------------------------------------------ //
    @Nested
    class BusinessRules {

        @Test
        void shouldThrowWhenHealthCareProfNotActive() {
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(false);

            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional not found or not active");

            verify(visitRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenMedicalSalesRepNotActive() {
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(false);

            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative not found or not active");

            verify(visitRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenDuplicateVisitKeyExists() {
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(true);
            when(visitRepository.existsByVisitKey(any(), any(), any())).thenReturn(true);

            CreateVisitInputDTO input = new CreateVisitInputDTO(
                VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already exists");

            verify(visitRepository, never()).save(any());
        }
    }
}
