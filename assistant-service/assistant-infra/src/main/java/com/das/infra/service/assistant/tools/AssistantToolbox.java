package com.das.infra.service.assistant.tools;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.assistant.audit.AgentAuditEntry;
import com.das.cleanddd.domain.assistant.audit.AgentAuditType;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAgentAuditTrail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The allow-list of tools and the one place they run. Every call and its result are written to the audit trail
 * before the result goes back to the model. An unknown tool, a broken contract or a failing tool gives the model
 * an error result; nothing a tool does can end the conversation with an exception.
 */
@Component
public class AssistantToolbox {

    private static final Logger log = LoggerFactory.getLogger(AssistantToolbox.class);
    static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, AssistantTool> tools = new LinkedHashMap<>();
    private final IAgentAuditTrail auditTrail;
    private final AssistantModelVersion version;
    private final Clock clock;

    public AssistantToolbox(List<AssistantTool> tools, IAgentAuditTrail auditTrail, AssistantModelVersion version,
                            Clock clock) {
        tools.forEach(tool -> {
            if (this.tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Two tools are named " + tool.name());
            }
        });
        this.auditTrail = auditTrail;
        this.version = version;
        this.clock = clock;
    }

    public List<AssistantTool> tools() {
        return List.copyOf(tools.values());
    }

    /**
     * @param error   whether {@code content} describes an error rather than a result
     * @param draftId the draft the tool proposed, or {@code null}
     */
    public record Execution(String content, boolean error, String draftId) {}

    public Execution run(String toolName, JsonNode input, ToolContext context) {
        String inputJson = input == null ? "null" : JSON.writeValueAsString(input);
        audit(context, AgentAuditType.TOOL_CALL, toolName + " " + inputJson, null);

        Execution execution;
        AssistantTool tool = tools.get(toolName);
        if (tool == null) {
            execution = error("Unknown tool: " + toolName);
        } else {
            try {
                AssistantTool.ToolResult result = tool.run(input, context);
                execution = new Execution(JSON.writeValueAsString(result.content()), false, result.draftId());
            } catch (ToolInputException e) {
                execution = error(e.getMessage());
            } catch (RuntimeException e) {
                log.error("Tool {} failed", toolName, e);
                execution = error("The tool failed; try again later or tell the rep.");
            }
        }
        audit(context, AgentAuditType.TOOL_RESULT, (execution.error() ? "ERROR " : "") + execution.content(),
                execution.draftId());
        return execution;
    }

    private static Execution error(String message) {
        return new Execution(JSON.writeValueAsString(Map.of("error", message)), true, null);
    }

    private void audit(ToolContext context, AgentAuditType type, String payload, String draftId) {
        auditTrail.append(AgentAuditEntry.of(context.conversationId(), type, AgentAuditEntry.ASSISTANT,
                version.modelId(), version.promptVersion(), payload, clock.instant(), draftId));
    }
}
