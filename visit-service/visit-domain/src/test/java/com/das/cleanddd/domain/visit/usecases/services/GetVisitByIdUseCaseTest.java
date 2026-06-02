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
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitIDDto;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

@ExtendWith(MockitoExtension.class)
class GetVisitByIdUseCaseTest {

    private static final String VISIT_ID = "123e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID   = "123e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID   = "123e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID  = "123e4567-e89b-12d3-a456-426614174004";

    @Mock private IVisitRepository visitRepository;

    private GetVisitByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetVisitByIdUseCase(visitRepository, new VisitMapper());
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
    class InputValidation {

        @Test
        void shouldThrowWhenInputIsNull() {
            assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(DomainException.class);
        }

        @Test
        void shouldThrowWhenVisitIdIsNull() {
            assertThatThrownBy(() -> useCase.execute(new VisitIDDto(null)))
                .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    class LookupBehavior {

        @Test
        void shouldThrowWhenVisitNotFound() {
            when(visitRepository.search(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute(new VisitIDDto(VISIT_ID)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not found");
        }

        @Test
        void shouldReturnOutputDTOWhenVisitExists() throws Exception {
            when(visitRepository.search(any())).thenReturn(Optional.of(buildVisit()));

            VisitOutputDTO output = useCase.execute(new VisitIDDto(VISIT_ID));

            assertThat(output).isNotNull();
            assertThat(output.id()).isEqualTo(VISIT_ID);
            assertThat(output.healthCareProfId()).isEqualTo(HCP_ID);
            assertThat(output.medicalSalesRepId()).isEqualTo(MSR_ID);
        }
    }
}
