package com.das.cleanddd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;
import org.springframework.web.client.RestTemplate;

class SettlementViewBeanTest {

    @Test
    void homePopulatesSettlementsInModel() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        SettlementViewBean controller = new SettlementViewBean(restTemplate);
        ReflectionTestUtils.setField(controller, "apiBaseUrl", "http://localhost:8080/api/v1/settlement");

        SettlementViewBean.SettlementSummary[] payload = new SettlementViewBean.SettlementSummary[] {
                new SettlementViewBean.SettlementSummary()
        };
        when(restTemplate.postForObject(any(String.class), any(), any(Class.class))).thenReturn(payload);

        Model model = mock(Model.class);
        String view = controller.home(model);

        assertEquals("index", view);
    }
}
