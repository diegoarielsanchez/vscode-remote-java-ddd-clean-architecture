package com.das.infra.service.assistant.claude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.services.blocking.MessageService;
import com.das.cleanddd.domain.assistant.ports.AssistantModelVersion;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel.AssistantReply;
import com.das.cleanddd.domain.assistant.ports.IAssistantModel.AssistantTurn;
import com.das.infra.service.assistant.tools.AssistantTool;
import com.das.infra.service.assistant.tools.AssistantToolbox;
import com.das.infra.service.assistant.tools.ToolContext;
import com.das.infra.service.assistant.tools.ToolSchema;

@DisplayName("ClaudeAssistantModel")
class ClaudeAssistantModelTest {

    private final MessageService messages = mock(MessageService.class);
    private final AnthropicClient client = mock(AnthropicClient.class);
    private final AssistantToolbox toolbox = mock(AssistantToolbox.class);
    private final ConversationHistory history = new ConversationHistory();
    private final AssistantTool tool = mock(AssistantTool.class);
    private final String msrId = UUID.randomUUID().toString();
    private final String conversationId = UUID.randomUUID().toString();
    private ClaudeAssistantModel model;

    @BeforeEach
    void setUp() {
        when(client.messages()).thenReturn(messages);
        when(tool.name()).thenReturn("search_products");
        when(tool.description()).thenReturn("Search the catalog");
        when(tool.schema()).thenReturn(ToolSchema.builder().string("name", "n").integer("page", "p").build());
        when(toolbox.tools()).thenReturn(List.of(tool));
        model = new ClaudeAssistantModel(client, toolbox, history, new SystemPrompt("system-test", "Be careful."),
                new AssistantModelVersion("claude-opus-5-5", "system-test:abc"), "medium", 3, 16000);
    }

    private static Message response(StopReason stop, ContentBlock... blocks) {
        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(blocks));
        when(message.stopReason()).thenReturn(Optional.ofNullable(stop));
        when(message.toParam()).thenReturn(MessageParam.ofUser("(assistant turn)"));
        return message;
    }

    private static ContentBlock toolUse(String name, Map<String, Object> input) {
        ToolUseBlock toolUse = mock(ToolUseBlock.class);
        when(toolUse.id()).thenReturn("toolu_" + UUID.randomUUID());
        when(toolUse.name()).thenReturn(name);
        when(toolUse._input()).thenReturn(JsonValue.from(input));
        ContentBlock block = mock(ContentBlock.class);
        when(block.toolUse()).thenReturn(Optional.of(toolUse));
        when(block.text()).thenReturn(Optional.empty());
        return block;
    }

    private static ContentBlock text(String value) {
        TextBlock textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(value);
        ContentBlock block = mock(ContentBlock.class);
        when(block.toolUse()).thenReturn(Optional.empty());
        when(block.text()).thenReturn(Optional.of(textBlock));
        return block;
    }

    private AssistantTurn turn(String text) {
        return new AssistantTurn(msrId, conversationId, text, null);
    }

    @Test
    @DisplayName("should run tools for the rep's context until the model answers, and keep the completed turn")
    void shouldRunToolLoop() throws Exception {
        Message proposes = response(StopReason.TOOL_USE, toolUse("propose_order_draft", Map.of("rationale", "r")));
        Message answers = response(StopReason.END_TURN, text("Draft ready: confirm it in the app."));
        when(messages.create(any(MessageCreateParams.class))).thenReturn(proposes, answers);
        when(toolbox.run(eq("propose_order_draft"), any(), any()))
                .thenReturn(new AssistantToolbox.Execution("{\"id\":\"d1\"}", false, "d1"));

        AssistantReply reply = model.converse(turn("Order 2 boxes of vaccine A"));

        assertEquals("Draft ready: confirm it in the app.", reply.text());
        assertEquals("d1", reply.draftId());
        ArgumentCaptor<ToolContext> context = ArgumentCaptor.forClass(ToolContext.class);
        verify(toolbox).run(eq("propose_order_draft"), any(), context.capture());
        assertEquals(new ToolContext(msrId, conversationId), context.getValue());
        // user message, assistant tool use, tool results, final answer
        assertEquals(4, history.of(conversationId).size());
    }

    @Test
    @DisplayName("should send the pinned model, the system prompt and strict tools with no extra properties")
    void shouldSendPinnedConfiguration() throws Exception {
        Message hi = response(StopReason.END_TURN, text("Hi"));
        when(messages.create(any(MessageCreateParams.class))).thenReturn(hi);

        model.converse(turn("Hello"));

        ArgumentCaptor<MessageCreateParams> sent = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messages).create(sent.capture());
        String body = ObjectMappers.jsonMapper().writeValueAsString(sent.getValue()._body());
        assertTrue(body.contains("\"model\":\"claude-opus-5-5\""), body);
        assertTrue(body.contains("Be careful."), body);
        assertTrue(body.contains("\"strict\":true"), body);
        assertTrue(body.contains("\"additionalProperties\":false"), body);
        assertTrue(body.contains("\"effort\":\"medium\""), body);
    }

    @Test
    @DisplayName("should stop at the step limit without keeping the unfinished turn")
    void shouldStopAtStepLimit() throws Exception {
        Message searches = response(StopReason.TOOL_USE, toolUse("search_products", Map.of("name", "a", "page", 1)));
        when(messages.create(any(MessageCreateParams.class))).thenReturn(searches);
        when(toolbox.run(any(), any(), any())).thenReturn(new AssistantToolbox.Execution("{}", false, null));

        AssistantReply reply = model.converse(turn("Loop forever"));

        assertEquals(ClaudeAssistantModel.CUT_SHORT, reply.text());
        verify(messages, times(3)).create(any(MessageCreateParams.class));
        assertTrue(history.of(conversationId).isEmpty());
    }

    @Test
    @DisplayName("should report a refusal to the rep and not keep that turn")
    void shouldHandleRefusal() throws Exception {
        Message refusal = response(StopReason.REFUSAL);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(refusal);

        AssistantReply reply = model.converse(turn("Something not allowed"));

        assertEquals(ClaudeAssistantModel.REFUSED, reply.text());
        assertNull(reply.draftId());
        assertTrue(history.of(conversationId).isEmpty());
    }
}
