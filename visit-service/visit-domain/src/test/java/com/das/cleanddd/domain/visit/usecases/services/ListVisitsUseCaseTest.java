package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

@ExtendWith(MockitoExtension.class)
class ListVisitsUseCaseTest {

    private static final String VISIT_ID = "323e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID   = "323e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID   = "323e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID  = "323e4567-e89b-12d3-a456-426614174004";

    @Mock private IVisitRepository visitRepository;

    private ListVisitsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVisitsUseCase(visitRepository, new VisitMapper());
    }

    private Visit buildVisit() throws BusinessValidationException {
        return new Visit(
            new VisitId(VISIT_ID),
            LocalDateTime.now().minusDays(1).withHour(10),
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
            when(visitRepository.searchAll()).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not found");
        }

        @Test
        void shouldThrowWhenRepositoryReturnsNull() {
            when(visitRepository.searchAll()).thenReturn(null);

            assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    class HappyPath {

        @Test
        void shouldReturnMappedListWhenVisitsExist() throws Exception {
            when(visitRepository.searchAll()).thenReturn(List.of(buildVisit()));

            List<VisitOutputDTO> result = useCase.execute();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(VISIT_ID);
        }

        @Test
        void shouldReturnAllVisits() throws Exception {
            when(visitRepository.searchAll()).thenReturn(
                List.of(buildVisit(), buildVisit())
            );

            List<VisitOutputDTO> result = useCase.execute();

            assertThat(result).hasSize(2);
        }
    }
}
