package com.das.infra.service.assistant.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.infra.service.assistant.persistence.InMemoryAgentAuditTrail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("AssistantToolbox")
class AssistantToolboxTest {

    private final InMemoryAgentAuditTrail audit = new InMemoryAgentAuditTrail();
    private final ToolContext context = new ToolContext(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    private static AssistantTool tool(String name, java.util.function.Function<JsonNode, Object> body) {
        return new AssistantTool() {
            public String name() { return name; }
            public String description() { return name; }
            public ToolSchema schema() { return ToolSchema.builder().string("q", "q").build(); }
            public ToolResult run(JsonNode input, ToolContext ctx) { return ToolResult.of(body.apply(input)); }
        };
    }

    private AssistantToolbox toolbox(AssistantTool... tools) {
        return new AssistantToolbox(List.of(tools), audit, new AssistantModelVersion("claude-opus-5-5", "v"),
                Clock.systemUTC());
    }

    private static JsonNode input() {
        return JsonMapper.builder().build().valueToTree(Map.of("q", "x"));
    }

    @Test
    @DisplayName("should audit the call and its result, and return the result as JSON")
    void shouldAuditCallAndResult() {
        AssistantToolbox.Execution execution = toolbox(tool("echo", in -> Map.of("ok", true)))
                .run("echo", input(), context);

        assertFalse(execution.error());
        assertEquals("{\"ok\":true}", execution.content());
        List<AgentAuditEntry> trail = audit.forConversation(context.conversationId());
        assertEquals(List.of(AgentAuditType.TOOL_CALL, AgentAuditType.TOOL_RESULT),
                trail.stream().map(AgentAuditEntry::type).toList());
        assertEquals("echo {\"q\":\"x\"}", trail.get(0).payload());
        assertEquals("claude-opus-5-5", trail.get(1).modelId());
    }

    @Test
    @DisplayName("should answer an unknown tool, a broken contract or a crash with an error result, never an exception")
    void shouldTurnFailuresIntoErrorResults() {
        AssistantToolbox toolbox = toolbox(
                tool("strict", in -> { throw new ToolInputException("q is too long"); }),
                tool("crash", in -> { throw new IllegalStateException("database password is hunter2"); }));

        AssistantToolbox.Execution unknown = toolbox.run("place_order", input(), context);
        AssistantToolbox.Execution invalid = toolbox.run("strict", input(), context);
        AssistantToolbox.Execution crash = toolbox.run("crash", input(), context);

        assertTrue(unknown.error());
        assertTrue(unknown.content().contains("Unknown tool: place_order"));
        assertTrue(invalid.content().contains("q is too long"));
        assertTrue(crash.error());
        assertFalse(crash.content().contains("hunter2"), "internal error details must not reach the model");
        assertEquals(6, audit.forConversation(context.conversationId()).size());
    }

    @Test
    @DisplayName("should refuse two tools with the same name")
    void shouldRefuseDuplicateNames() {
        assertThrows(IllegalStateException.class, () -> toolbox(tool("a", in -> 1), tool("a", in -> 2)));
    }
}
