package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@ExtendWith(MockitoExtension.class)
class GetVisitPlanByIdUseCaseTest {

    private static final String PLAN_ID = "223e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID  = "223e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID  = "223e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID = "223e4567-e89b-12d3-a456-426614174004";

    @Mock private IVisitPlanRepository visitPlanRepository;

    private GetVisitPlanByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetVisitPlanByIdUseCase(visitPlanRepository, new VisitPlanMapper());
    }

    private VisitPlan buildPlan() throws BusinessValidationException {
        return new VisitPlan(
            new VisitId(PLAN_ID),
            new VisitDateTime(LocalDateTime.now().plusDays(1)),
            new HealthCareProfId(HCP_ID),
            null,
            new Identifier(SITE_ID) {},
            List.of(),
            new MedicalSalesRepId(MSR_ID)
        );
    }

    @Nested
    class InputValidation {

        @Test
        void shouldThrowWhenInputIsNull() {
            assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(DomainException.class);
        }

        @Test
        void shouldThrowWhenPlanIdIsNull() {
            assertThatThrownBy(() -> useCase.execute(new VisitPlanIDDto(null)))
                .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    class LookupBehavior {

        @Test
        void shouldThrowWhenVisitPlanNotFound() {
            when(visitPlanRepository.search(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(new VisitPlanIDDto(PLAN_ID)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not found");
        }

        @Test
        void shouldReturnOutputDTOWhenPlanExists() throws Exception {
            when(visitPlanRepository.search(any())).thenReturn(Optional.of(buildPlan()));

            VisitPlanOutputDTO output = useCase.execute(new VisitPlanIDDto(PLAN_ID));

            assertThat(output).isNotNull();
            assertThat(output.id()).isEqualTo(PLAN_ID);
            assertThat(output.healthCareProfId()).isEqualTo(HCP_ID);
            assertThat(output.medicalSalesRepId()).isEqualTo(MSR_ID);
        }
    }
}
