package com.das.cleanddd.domain.assistant.audit;

public enum AgentAuditType {
    /** What the rep asked. */
    USER_MESSAGE,
    /** A tool the model asked to run, with its input. */
    TOOL_CALL,
    /** What the tool returned to the model, or the error it raised. */
    TOOL_RESULT,
    /** A draft the assistant proposed. */
    PROPOSAL,
    /** The model's answer to the rep. */
    MODEL_REPLY,
    /** The rep confirmed or discarded a draft. */
    HUMAN_DECISION
}
