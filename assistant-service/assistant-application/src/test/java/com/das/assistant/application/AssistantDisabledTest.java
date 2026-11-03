package com.das.assistant.application;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.anthropic.client.AnthropicClient;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel;
import com.das.infra.service.assistant.claude.DisabledAssistantModel;

/** With the default configuration the assistant is off: no model client, no endpoints, no API key needed. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "jwt.secret=" + TestTokens.JWT_SECRET
})
@DisplayName("Assistant disabled (default)")
class AssistantDisabledTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("should create no Claude client and use the disabled model")
    void shouldNotCreateClient() {
        assertTrue(context.getBeansOfType(AnthropicClient.class).isEmpty());
        assertInstanceOf(DisabledAssistantModel.class, context.getBean(IAssistantModel.class));
    }

    @Test
    @DisplayName("should not expose the assistant's endpoints, even to a rep")
    void shouldNotExposeEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/messages")
                        .header("Authorization", TestTokens.bearerForRep(UUID.randomUUID().toString(), "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello\"}"))
                .andExpect(status().isNotFound());
    }
}
