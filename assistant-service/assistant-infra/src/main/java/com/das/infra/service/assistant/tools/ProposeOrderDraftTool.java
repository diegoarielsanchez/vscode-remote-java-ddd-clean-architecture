package com.das.infra.service.assistant.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.assistant.entities.OrderDraft;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftLineInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.ProposeOrderDraftInputDTO;
import com.das.cleanddd.domain.assistant.usecases.services.DraftProposalFactory;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import tools.jackson.databind.JsonNode;

/**
 * Proposes an order draft for the rep to review. This is as far as the assistant can go: the draft is checked and
 * stored, nothing is reserved or ordered, and only the rep can confirm it.
 */
@Component
public class ProposeOrderDraftTool implements AssistantTool {

    private static final Map<String, Object> LINE = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("productId", "quantity"),
            "properties", Map.of(
                    "productId", Map.of("type", "string", "description", "A product id returned by search_products."),
                    "quantity", Map.of("type", "integer", "description", "Units, from 1 to "
                            + CheckAvailabilityTool.MAX_QUANTITY + ".")));

    private static final ToolSchema SCHEMA = ToolSchema.builder()
            .array("lines", "The products and quantities, 1 to " + OrderDraft.MAX_LINES
                    + " lines, each product at most once.", LINE)
            .nullableString("contextHcpId", "The HCP this order is prepared for, from find_hcp or get_visit, or"
                    + " null. It is recorded for the audit trail; the order itself names no HCP.")
            .string("rationale", "In one or two sentences, why these products and quantities; at most "
                    + OrderDraft.MAX_RATIONALE_LENGTH + " characters. The rep reads it before confirming.")
            .build();

    private final DraftProposalFactory proposals;

    public ProposeOrderDraftTool(DraftProposalFactory proposals) {
        this.proposals = proposals;
    }

    @Override
    public String name() {
        return "propose_order_draft";
    }

    @Override
    public String description() {
        return "Propose an order draft for the rep to review and confirm. It does not place an order. The catalog"
                + " checks every line (product exists and is active, valid GTIN, enough stock) and flags storage"
                + " conditions; the result lists each check. Tell the rep about every FAIL and WARN.";
    }

    @Override
    public ToolSchema schema() {
        return SCHEMA;
    }

    @Override
    public ToolResult run(JsonNode input, ToolContext context) {
        ToolInputs.onlyFields(input, SCHEMA);
        JsonNode lines = input.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty() || lines.size() > OrderDraft.MAX_LINES) {
            throw new ToolInputException("lines must hold 1 to " + OrderDraft.MAX_LINES + " lines.");
        }
        List<DraftLineInputDTO> proposed = new ArrayList<>();
        for (JsonNode line : lines) {
            if (line.size() != 2) {
                throw new ToolInputException("Each line has exactly productId and quantity.");
            }
            proposed.add(new DraftLineInputDTO(ToolInputs.id(line, "productId"),
                    ToolInputs.integer(line, "quantity", 1, CheckAvailabilityTool.MAX_QUANTITY)));
        }
        String contextHcpId = ToolInputs.nullableId(input, "contextHcpId");
        String rationale = ToolInputs.text(input, "rationale", 1, OrderDraft.MAX_RATIONALE_LENGTH);
        try {
            OrderDraftOutputDTO draft = proposals.getProposeOrderDraftUseCase().execute(new ProposeOrderDraftInputDTO(
                    context.medicalSalesRepId(), context.conversationId(), proposed, contextHcpId, rationale));
            return new ToolResult(draft, draft.id());
        } catch (DomainException e) {
            throw new ToolInputException(e.getMessage());
        }
    }
}
