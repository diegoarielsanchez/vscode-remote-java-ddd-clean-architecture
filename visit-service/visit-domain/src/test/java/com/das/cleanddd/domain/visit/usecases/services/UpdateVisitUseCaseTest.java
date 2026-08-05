package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

@ExtendWith(MockitoExtension.class)
class UpdateVisitUseCaseTest {

    private static final String    VISIT_ID   = "523e4567-e89b-12d3-a456-426614174001";
    private static final String    HCP_ID     = "523e4567-e89b-12d3-a456-426614174002";
    private static final String    MSR_ID     = "523e4567-e89b-12d3-a456-426614174003";
    private static final String    SITE_ID    = "523e4567-e89b-12d3-a456-426614174004";
    private static final LocalDate VALID_DATE = LocalDate.now().minusDays(1);

    @Mock private IVisitRepository          visitRepository;
    @Mock private IHealthCareProfValidator  healthCareProfValidator;
    @Mock private IMedicalSalesRepValidator medicalSalesRepValidator;

    private UpdateVisitUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateVisitUseCase(
            visitRepository,
            healthCareProfValidator,
            medicalSalesRepValidator,
            new VisitMapper()
        );
    }

    private Visit buildExistingVisit() throws BusinessValidationException {
        return new Visit(
            new VisitId(VISIT_ID),
            new VisitDateTime(LocalDateTime.now().minusDays(1).withHour(10)),
            new HealthCareProfId(HCP_ID),
            null,
            new Identifier(SITE_ID) {},
            List.of(),
            new MedicalSalesRepId(MSR_ID)
        );
    }

    private UpdateVisitInputDTO validInput() {
        return new UpdateVisitInputDTO(VISIT_ID, VALID_DATE, HCP_ID, "updated comment", SITE_ID, MSR_ID);
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
        void shouldThrowWhenVisitIdIsNull() {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO(null, VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit id");
        }

        @Test
        void shouldThrowWhenVisitIdIsBlank() {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO("  ", VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit id");
        }

        @Test
        void shouldThrowWhenVisitDateIsNull() {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO(VISIT_ID, null, HCP_ID, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("date cannot be null");
        }

        @Test
        void shouldThrowWhenHealthCareProfIdIsNull() {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO(VISIT_ID, VALID_DATE, null, null, SITE_ID, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional");
        }

        @Test
        void shouldThrowWhenVisitSiteIdIsNull() {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO(VISIT_ID, VALID_DATE, HCP_ID, null, null, MSR_ID);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit site id");
        }

        @Test
        void shouldThrowWhenMedicalSalesRepIdIsNull() {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO(VISIT_ID, VALID_DATE, HCP_ID, null, SITE_ID, null);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative");
        }
    }

    @Nested
    class BusinessRules {

        @Test
        void shouldThrowWhenVisitNotFound() {
            when(visitRepository.search(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(validInput()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not found");

            verify(visitRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenHealthCareProfNotActive() throws BusinessValidationException {
            when(visitRepository.search(any())).thenReturn(Optional.of(buildExistingVisit()));
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(false);

            assertThatThrownBy(() -> useCase.execute(validInput()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Health Care Professional not found or not active");

            verify(visitRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenMedicalSalesRepNotActive() throws BusinessValidationException {
            when(visitRepository.search(any())).thenReturn(Optional.of(buildExistingVisit()));
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(false);

            assertThatThrownBy(() -> useCase.execute(validInput()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Medical Sales Representative not found or not active");

            verify(visitRepository, never()).save(any());
        }
    }

    @Nested
    class HappyPath {

        @BeforeEach
        void setUp() throws BusinessValidationException {
            when(visitRepository.search(any())).thenReturn(Optional.of(buildExistingVisit()));
            when(healthCareProfValidator.existsAndActive(HCP_ID)).thenReturn(true);
            when(medicalSalesRepValidator.existsAndActive(MSR_ID)).thenReturn(true);
        }

        @Test
        void shouldSaveAndReturnUpdatedVisit() throws DomainException {
            VisitOutputDTO output = useCase.execute(validInput());

            assertThat(output).isNotNull();
            assertThat(output.healthCareProfId()).isEqualTo(HCP_ID);
            assertThat(output.medicalSalesRepId()).isEqualTo(MSR_ID);
            assertThat(output.visitComments()).isEqualTo("updated comment");
            verify(visitRepository).save(any());
        }

        @Test
        void shouldAllowNullComments() throws DomainException {
            UpdateVisitInputDTO input = new UpdateVisitInputDTO(VISIT_ID, VALID_DATE, HCP_ID, null, SITE_ID, MSR_ID);

            VisitOutputDTO output = useCase.execute(input);

            assertThat(output.visitComments()).isNull();
        }
    }
}
