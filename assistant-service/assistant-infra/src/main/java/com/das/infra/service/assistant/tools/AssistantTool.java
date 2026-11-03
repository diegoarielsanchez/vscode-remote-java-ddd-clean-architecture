package com.das.infra.service.assistant.tools;

import tools.jackson.databind.JsonNode;

/**
 * A tool the assistant's model may call. Tools only read from the other services or propose a draft; none can
 * place an order or change any other service's data.
 */
public interface AssistantTool {

    String name();

    String description();

    ToolSchema schema();

    /**
     * Runs the tool for the rep in {@code context}.
     *
     * @throws ToolInputException when the input breaks the tool's contract
     */
    ToolResult run(JsonNode input, ToolContext context);

    /**
     * What goes back to the model, as JSON.
     *
     * @param draftId the draft the tool proposed, or {@code null}
     */
    record ToolResult(Object content, String draftId) {

        public static ToolResult of(Object content) {
            return new ToolResult(content, null);
        }
    }
}
