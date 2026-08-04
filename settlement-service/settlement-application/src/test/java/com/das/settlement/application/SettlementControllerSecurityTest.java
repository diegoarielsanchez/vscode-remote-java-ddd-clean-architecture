package com.das.settlement.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.das.cleanddd.domain.settlement.usecases.dtos.CreateSettlementInputDTO;
import com.das.cleanddd.domain.settlement.usecases.dtos.SettlementOutputDTO;
import com.das.cleanddd.domain.settlement.usecases.services.SettlementUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.settlement.application.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SettlementController.class)
@Import({SecurityConfig.class, SettlementControllerSecurityTest.StubbedUseCases.class})
class SettlementControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createSettlementEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/settlement/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"demo\",\"settlementDate\":\"2026-07-28\",\"invoices\":[],\"medicalSalesRepId\":\"msr-1\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSettlementEndpointIsAccessibleWithUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/settlement/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"demo\",\"settlementDate\":\"2026-07-28\",\"invoices\":[],\"medicalSalesRepId\":\"msr-1\"}"))
            .andExpect(status().isCreated());
    }

    /**
     * Provides a pre-stubbed {@link SettlementUseCaseFactory} bean.
     * <p>
     * Stubbing must happen inside the {@code @Bean} method (not in
     * {@code @BeforeEach}) because {@link SettlementController}'s constructor
     * eagerly resolves each use case from the factory when the (cached)
     * ApplicationContext is first built — before any {@code @BeforeEach}
     * method runs.
     */
    @TestConfiguration
    static class StubbedUseCases {

        @SuppressWarnings("unchecked")
        @Bean
        SettlementUseCaseFactory settlementUseCaseFactory() throws Exception {
            SettlementUseCaseFactory factory = mock(SettlementUseCaseFactory.class);

            UseCase<CreateSettlementInputDTO, SettlementOutputDTO> createUseCase = mock(UseCase.class);
            when(createUseCase.execute(any())).thenReturn(new SettlementOutputDTO(
                    "settlement-1", "demo", LocalDate.parse("2026-07-28"), "OPEN",
                    BigDecimal.ZERO, List.of(), "msr-1"));

            when(factory.getCreateSettlementUseCase()).thenReturn(createUseCase);
            when(factory.getUpdateSettlementUseCase()).thenReturn(mock(UseCase.class));
            when(factory.getSettlementByIdUseCase()).thenReturn(mock(UseCase.class));
            when(factory.getListSettlementsUseCase()).thenReturn(mock(UseCase.class));
            when(factory.getAddInvoiceUseCase()).thenReturn(mock(UseCase.class));
            when(factory.getRemoveInvoiceUseCase()).thenReturn(mock(UseCase.class));
            return factory;
        }
    }
}
