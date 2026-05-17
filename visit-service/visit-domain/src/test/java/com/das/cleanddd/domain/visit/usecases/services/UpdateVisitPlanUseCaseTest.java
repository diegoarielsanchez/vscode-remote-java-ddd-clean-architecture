package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@ExtendWith(MockitoExtension.class)
class UpdateVisitPlanUseCaseTest {

    private static final String        PLAN_ID    = "623e4567-e89b-12d3-a456-426614174001";
    private static final String        HCP_ID     = "623e4567-e89b-12d3-a456-426614174002";
    private static final String        MSR_ID     = "623e4567-e89b-12d3-a456-426614174003";
    private static final String        SITE_ID    = "623e4567-e89b-12d3-a456-426614174004";
    private static final LocalDateTime FUTURE_DT  = LocalDateTime.now().plusDays(1);

    @Mock private IVisitPlanRepository      visitPlanRepository;
    @Mock private IHealthCareProfValidator  healthCareProfValidator;
    @Mock private IMedicalSalesRepValidator medicalSalesRepValidator;

    private UpdateVisitPlanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateVisitPlanUseCase(
            visitPlanRepository,
            healthCareProfValidator,
            medicalSalesRepValidator,
            new VisitPlanMapper()
        );
    }

    private VisitPlan buildExistingPlan() throws BusinessValidationException {
        return new VisitPlan(
            new VisitId(PLAN_ID),
            FUTURE_DT,
            new HealthCareProfId(HCP_ID),
            null,
            new Identifier(SITE_ID) {},
            List.of(),
            new MedicalSalesRepId(MSR_ID)
        );
    }

    private UpdateVisitPlanInputDTO validInput() {
        return new UpdateVisitPlanInputDTO(PLAN_ID, FUTURE_DT, HCP_ID, "updated notes", SITE_ID, MSR_ID);
    }

    @Nested
    class InputValidation {

        @Test
        void shouldThrowWhenInputIsNull() {
            assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("null");
        }

        @Test
        void shouldThrowWhenPlanIdIsNull() {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO(null, FUTURE_DT, HCP_ID, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit plan id");
        }

        @Test
        void shouldThrowWhenPlanIdIsBlank() {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO("", FUTURE_DT, HCP_ID, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit plan id");
        }

        @Test
        void shouldThrowWhenVisitDateTimeIsNull() {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO(PLAN_ID, null, HCP_ID, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("date time cannot be null");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsNull() {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO(PLAN_ID, FUTURE_DT, null, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional");
        }

        @Test
        void shouldThrowWhenVisitSiteIdIsNull() {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO(PLAN_ID, FUTURE_DT, HCP_ID, null, null, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit site id");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsNull() {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO(PLAN_ID, FUTURE_DT, HCP_ID, null, SITE_ID, null);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative");
        }
    }

    @Nested
    class BusinessRules {

        @Test
        void shouldThrowWhenVisitPlanNotFound() {
            when(visitPlanRepository.search(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(validInput()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not found");

            verify(visitPlanRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenHealthCareProfNotActive() throws BusinessValidationException {
            when(visitPlanRepository.search(any())).thenReturn(Optional.of(buildExistingPlan()));
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(false);

            assertThatThrownBy(() -> useCase.execute(validInput()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional not found or not active");

            verify(visitPlanRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenMedicalSalesRepNotActive() throws BusinessValidationException {
            when(visitPlanRepository.search(any())).thenReturn(Optional.of(buildExistingPlan()));
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(false);

            assertThatThrownBy(() -> useCase.execute(validInput()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative not found or not active");

            verify(visitPlanRepository, never()).save(any());
        }
    }

    @Nested
    class HappyPath {

        @BeforeEach
        void setUp() throws BusinessValidationException {
            when(visitPlanRepository.search(any())).thenReturn(Optional.of(buildExistingPlan()));
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(true);
        }

        @Test
        void shouldSaveAndReturnUpdatedPlan() throws DomainException {
            VisitPlanOutputDTO output = useCase.execute(validInput());

            assertThat(output).isNotNull();
            assertThat(output.healthCareProfId()).isEqualTo(HCP_ID);
            assertThat(output.medicalSalesRepId()).isEqualTo(MSR_ID);
            assertThat(output.visitComments()).isEqualTo("updated notes");
            verify(visitPlanRepository).save(any());
        }

        @Test
        void shouldAllowNullComments() throws DomainException {
            UpdateVisitPlanInputDTO input = new UpdateVisitPlanInputDTO(PLAN_ID, FUTURE_DT, HCP_ID, null, SITE_ID, MSR_ID);

            VisitPlanOutputDTO output = useCase.execute(input);

            assertThat(output.visitComments()).isNull();
        }
    }
}
