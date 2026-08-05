package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
import com.das.cleanddd.domain.visit.usecases.dtos.ListVisitPlansInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@ExtendWith(MockitoExtension.class)
class ListVisitPlansUseCaseTest {

    private static final String PLAN_ID = "423e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID  = "423e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID  = "423e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID = "423e4567-e89b-12d3-a456-426614174004";

    @Mock private IVisitPlanRepository visitPlanRepository;

    private ListVisitPlansUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVisitPlansUseCase(visitPlanRepository, new VisitPlanMapper());
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
    class EmptyRepository {

        @Test
        void shouldThrowWhenRepositoryReturnsEmptyList() {
            when(visitPlanRepository.searchAll(anyInt(), anyInt())).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> useCase.execute(new ListVisitPlansInputDTO(1, 10)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("No visit plans found");
        }

        @Test
        void shouldThrowWhenRepositoryReturnsNull() {
            when(visitPlanRepository.searchAll(anyInt(), anyInt())).thenReturn(null);

            assertThatThrownBy(() -> useCase.execute(new ListVisitPlansInputDTO(1, 10)))
                .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    class HappyPath {

        @Test
        void shouldReturnMappedListWhenPlansExist() throws Exception {
            when(visitPlanRepository.searchAll(anyInt(), anyInt())).thenReturn(List.of(buildPlan()));

            List<VisitPlanOutputDTO> result = useCase.execute(new ListVisitPlansInputDTO(1, 10));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(PLAN_ID);
        }

        @Test
        void shouldReturnAllVisitPlans() throws Exception {
            when(visitPlanRepository.searchAll(anyInt(), anyInt())).thenReturn(
                List.of(buildPlan(), buildPlan())
            );

            List<VisitPlanOutputDTO> result = useCase.execute(new ListVisitPlansInputDTO(1, 10));

            assertThat(result).hasSize(2);
        }
    }
}
