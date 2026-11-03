package com.das.assistant.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger UI comes from a webjar pinned above the version springdoc ships (DOMPurify CVE fix), so springdoc must be
 * told the pinned version ({@code springdoc.swagger-ui.version}). If the two drift apart the UI answers 404: this
 * test catches that. Prod disables both endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "jwt.secret=" + TestTokens.JWT_SECRET,
        "assistant.enabled=true",
        "assistant.anthropic.api-key=test-key-never-used"
})
@DisplayName("OpenAPI and Swagger UI")
class OpenApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should serve the API description and the pinned Swagger UI")
    void shouldServeDocsAndUi() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(docs.contains("/api/v1/assistant/messages"), docs);

        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
    }
}
