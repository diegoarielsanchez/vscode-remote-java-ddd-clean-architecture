package com.das.infra.service.assistant.tools;

/** The model's tool input broke the tool's contract. Returned to the model as an error result, never thrown further. */
public class ToolInputException extends RuntimeException {

    public ToolInputException(String message) {
        super(message);
    }
}
