package com.das.infra.service.assistant.claude;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.models.messages.MessageParam;
import com.das.infra.service.assistant.persistence.NonDurableStore;

/**
 * The messages of each conversation as sent to the model, kept so the next turn continues it. Only completed turns
 * are stored, and a stored turn is never edited: the history only grows.
 */
@Component
@ConditionalOnProperty(name = "assistant.enabled", havingValue = "true")
public class ConversationHistory implements NonDurableStore {

    private final ConcurrentMap<String, List<MessageParam>> conversations = new ConcurrentHashMap<>();

    public List<MessageParam> of(String conversationId) {
        return conversations.getOrDefault(conversationId, List.of());
    }

    /** Stores a completed turn: {@code messages} is the previous history plus the new turn's messages. */
    public void completeTurn(String conversationId, List<MessageParam> messages) {
        conversations.put(conversationId, List.copyOf(messages));
    }
}
