package com.das.infra.service.assistant.claude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.infra.service.assistant.tools.AssistantTool;
import com.das.infra.service.assistant.tools.AssistantToolbox;
import com.das.infra.service.assistant.tools.ToolContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link IAssistantModel} on the Claude API, with the official Java SDK and a tool loop written out here rather
 * than a beta helper: every step is visible, and only generally available API features are used.
 *
 * <p>One turn runs at most {@code assistant.max-steps-per-message} model calls. A turn is added to the
 * conversation's history only when it ends normally; a turn cut short (step limit, output limit, refusal) is not
 * kept, so the history always holds complete exchanges. Every tool call is audited by {@link AssistantToolbox}.
 *
 * <p>No automatic fallback to another model is configured: the model is part of the validated configuration, so a
 * refusal is reported to the rep rather than retried on a model nobody validated.
 */
@Service
@ConditionalOnProperty(name = "assistant.enabled", havingValue = "true")
public class ClaudeAssistantModel implements IAssistantModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAssistantModel.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final String REFUSED = "I can't help with that request.";
    static final String CUT_SHORT = "I could not finish preparing an answer. Please ask again, more specifically.";

    private final AnthropicClient client;
    private final AssistantToolbox toolbox;
    private final ConversationHistory history;
    private final SystemPrompt systemPrompt;
    private final AssistantModelVersion version;
    private final OutputConfig.Effort effort;
    private final int maxSteps;
    private final long maxTokens;
    private final List<Tool> tools;

    public ClaudeAssistantModel(AnthropicClient client, AssistantToolbox toolbox, ConversationHistory history,
                                SystemPrompt systemPrompt, AssistantModelVersion version,
                                @Value("${assistant.model.effort:medium}") String effort,
                                @Value("${assistant.max-steps-per-message:8}") int maxSteps,
                                @Value("${assistant.model.max-tokens:16000}") long maxTokens) {
        this.client = client;
        this.toolbox = toolbox;
        this.history = history;
        this.systemPrompt = systemPrompt;
        this.version = version;
        this.effort = OutputConfig.Effort.of(effort);
        this.maxSteps = maxSteps;
        this.maxTokens = maxTokens;
        this.tools = toolbox.tools().stream().map(ClaudeAssistantModel::toSdkTool).toList();
    }

    /** A tool's contract as the API expects it: strict, every property required, nothing else allowed. */
    static Tool toSdkTool(AssistantTool tool) {
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        tool.schema().properties().forEach((name, schema) -> properties.putAdditionalProperty(name, JsonValue.from(schema)));
        return Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required(tool.schema().required())
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    @Override
    public AssistantReply converse(AssistantTurn turn) throws DomainException {
        List<MessageParam> messages = new ArrayList<>(history.of(turn.conversationId()));
        messages.add(MessageParam.ofUser(userText(turn)));
        ToolContext context = new ToolContext(turn.medicalSalesRepId(), turn.conversationId());
        String draftId = null;

        for (int step = 0; step < maxSteps; step++) {
            Message response = call(messages);
            messages.add(response.toParam());
            StopReason stop = response.stopReason().orElse(null);
            List<ToolUseBlock> toolUses = response.content().stream()
                    .flatMap(block -> block.toolUse().stream())
                    .toList();

            if (StopReason.REFUSAL.equals(stop)) {
                return new AssistantReply(REFUSED, draftId);
            }
            if (StopReason.MAX_TOKENS.equals(stop)) {
                return new AssistantReply(CUT_SHORT, draftId);
            }
            if (toolUses.isEmpty()) {
                history.completeTurn(turn.conversationId(), messages);
                return new AssistantReply(text(response), draftId);
            }

            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock toolUse : toolUses) {
                AssistantToolbox.Execution execution = toolbox.run(toolUse.name(), input(toolUse), context);
                if (execution.draftId() != null) {
                    draftId = execution.draftId();
                }
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(execution.content())
                        .isError(execution.error())
                        .build()));
            }
            messages.add(MessageParam.ofUserOfContentBlockParams(results));
        }
        log.warn("Conversation {} hit the {}-step limit", turn.conversationId(), maxSteps);
        return new AssistantReply(CUT_SHORT, draftId);
    }

    private Message call(List<MessageParam> messages) throws DomainException {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(version.modelId())
                .maxTokens(maxTokens)
                .system(systemPrompt.text())
                .outputConfig(OutputConfig.builder().effort(effort).build())
                .messages(messages);
        tools.forEach(params::addTool);
        try {
            return client.messages().create(params.build());
        } catch (AnthropicException e) {
            log.error("Claude API call failed: {}", e.getMessage());
            throw new DomainException("The assistant is not available right now.");
        }
    }

    /** The visit id, when given, is a UUID checked by the controller; it is a reference, not an instruction. */
    private static String userText(AssistantTurn turn) {
        return turn.visitId() == null
                ? turn.text()
                : turn.text() + "\n\n(The rep is looking at visit " + turn.visitId() + ".)";
    }

    private static JsonNode input(ToolUseBlock toolUse) {
        return JSON.valueToTree(toolUse._input().convert(Map.class));
    }

    private static String text(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"))
                .strip();
    }

    /** For tests: the tools as sent to the API. */
    List<Tool> sdkTools() {
        return tools;
    }
}
