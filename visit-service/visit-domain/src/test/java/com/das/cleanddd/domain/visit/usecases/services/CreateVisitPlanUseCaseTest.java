package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.VisitPlanFactory;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@ExtendWith(MockitoExtension.class)
class CreateVisitPlanUseCaseTest {

    private static final String        HCP_ID     = "123e4567-e89b-12d3-a456-426614174002";
    private static final String        MSR_ID     = "123e4567-e89b-12d3-a456-426614174003";
    private static final String        SITE_ID    = "123e4567-e89b-12d3-a456-426614174004";
    /** Tomorrow at 10:00 — always in the future */
    private static final LocalDateTime VALID_DT   = LocalDateTime.now().plusDays(1).withHour(10);

    @Mock private IVisitPlanRepository      visitPlanRepository;
    @Mock private IHealthCareProfValidator  healthCareProfValidator;
    @Mock private IMedicalSalesRepValidator medicalSalesRepValidator;

    private CreateVisitPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateVisitPlanUseCase(
            visitPlanRepository,
            new VisitPlanFactory(healthCareProfValidator, medicalSalesRepValidator),
            new VisitPlanMapper()
        );
    }

    // ------------------------------------------------------------------ //
    @Nested
    class HappyPath {

        @BeforeEach
        void setUpHappy() {
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(true);
        }

        @Test
        void shouldCreateVisitPlanAndReturnOutputDTO() throws DomainException {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, "planned visit", SITE_ID, MSR_ID
            );

            VisitPlanOutputDTO output = useCase.execute(input);

            assertThat(output).isNotNull();
            assertThat(output.id()).isNotBlank();
            assertThat(output.visitDateTime()).isEqualTo(VALID_DT);
            assertThat(output.healthCareProfId()).isEqualTo(HCP_ID);
            assertThat(output.medicalSalesRepId()).isEqualTo(MSR_ID);
            assertThat(output.visitSiteId()).isEqualTo(SITE_ID);
            assertThat(output.visitComments()).isEqualTo("planned visit");
            verify(visitPlanRepository).save(any());
        }

        @Test
        void shouldCreateVisitPlanWithNullComments() throws DomainException {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, SITE_ID, MSR_ID
            );

            VisitPlanOutputDTO output = useCase.execute(input);

            assertThat(output.visitComments()).isNull();
            verify(visitPlanRepository).save(any());
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
        void shouldThrowWhenVisitDateTimeIsNull() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                null, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("date time cannot be null");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsNull() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, null, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional id");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsBlank() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, "  ", null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional id");
        }

        @Test
        void shouldThrowWhenVisitSiteIdIsNull() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, null, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit site id");
        }

        @Test
        void shouldThrowWhenVisitSiteIdIsBlank() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, "", MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit site id");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsNull() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, SITE_ID, null
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative id");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsBlank() {
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, SITE_ID, "   "
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

            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional not found or not active");

            verify(visitPlanRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenMedicalSalesRepNotActive() {
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(false);

            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                VALID_DT, HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative not found or not active");

            verify(visitPlanRepository, never()).save(any());
        }

        @Test
        void shouldWrapIllegalArgumentExceptionInDomainException() {
            // visitDateTime in the past triggers BusinessValidationException inside the factory
            // before participant validation, so no validator stubs are needed
            CreateVisitPlanInputDTO input = new CreateVisitPlanInputDTO(
                LocalDateTime.now().minusDays(1), HCP_ID, null, SITE_ID, MSR_ID
            );
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class);

            verify(visitPlanRepository, never()).save(any());
        }
    }
}
