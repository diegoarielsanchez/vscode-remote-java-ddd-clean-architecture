package com.das.infra.service.assistant.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.assistant.ports.IHcpPort;

import tools.jackson.databind.JsonNode;

@Component
public class FindHcpTool implements AssistantTool {

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .string("lastName", "The HCP's last name or its beginning, 2 to 80 characters.")
            .integer("page", "Result page, from 1 to 20. Each page has up to 10 HCPs.")
            .build();

    private final IHcpPort hcps;

    public FindHcpTool(IHcpPort hcps) {
        this.hcps = hcps;
    }

    @Override
    public String name() {
        return "find_hcp";
    }

    @Override
    public String description() {
        return "Find healthcare professionals (HCPs) by last name. Returns id, name, surname, specialties and whether"
                + " the HCP is active. Use it to identify the HCP the rep is talking about; an order itself never"
                + " names an HCP.";
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public ToolResult run(JsonNode input, ToolContext context) {
        ToolInputs.onlyFields(input, SCHEMA);
        String lastName = ToolInputs.text(input, "lastName", 2, 80);
        int page = ToolInputs.integer(input, "page", 1, 20);
        return ToolResult.of(Map.of("hcps", hcps.findHcps(lastName, page)));
    }
}
