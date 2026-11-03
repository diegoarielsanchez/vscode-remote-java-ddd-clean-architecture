package com.das.assistant.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.das.assistant.application.security.JwtCallerDetails;
import com.das.cleanddd.domain.assistant.usecases.dtos.AskAssistantInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.AssistantReplyDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.DraftDecisionInputDTO;
import com.das.cleanddd.domain.assistant.usecases.dtos.OrderDraftOutputDTO;
import com.das.cleanddd.domain.assistant.usecases.services.AskAssistantUseCase;
import com.das.cleanddd.domain.assistant.usecases.services.AssistantUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The rep's assistant: ask it to prepare an order, then confirm or discard the draft it proposes. Exists only when
 * {@code assistant.enabled=true}; otherwise these paths answer 404.
 *
 * <p>The rep is always the caller's own (token {@code msr_id}), never a request value (OWASP A01).
 */
@RestController
@ConditionalOnProperty(name = "assistant.enabled", havingValue = "true")
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/assistant")
@Tag(name = "Assistant", description = "Order drafting assistant for Medical Sales Reps; drafts need the rep's confirmation")
@SecurityRequirement(name = "bearerAuth")
public class AssistantController {

    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final UseCase<AskAssistantInputDTO, AssistantReplyDTO> askAssistantUseCase;
    private final UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> getDraftUseCase;
    private final UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> confirmDraftUseCase;
    private final UseCase<DraftDecisionInputDTO, OrderDraftOutputDTO> discardDraftUseCase;
    private final AssistantRateLimiter rateLimiter;

    public AssistantController(AssistantUseCaseFactory factory, AssistantRateLimiter rateLimiter) {
        this.askAssistantUseCase = factory.getAskAssistantUseCase();
        this.getDraftUseCase = factory.getGetDraftUseCase();
        this.confirmDraftUseCase = factory.getConfirmDraftUseCase();
        this.discardDraftUseCase = factory.getDiscardDraftUseCase();
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/messages")
    @Operation(summary = "Send the assistant a message; omit conversationId to start a new conversation")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<AssistantReplyDTO> ask(@Valid @RequestBody AskRequest body, Authentication authentication)
            throws DomainException {
        String msrId = callerMsrId(authentication);
        rateLimiter.acquire(msrId);
        return ResponseEntity.ok(askAssistantUseCase.execute(
                new AskAssistantInputDTO(msrId, body.conversationId(), body.text(), body.visitId())));
    }

    @GetMapping("/drafts/{id}")
    @Operation(summary = "Get one of the caller's order drafts, with its checks")
    public ResponseEntity<OrderDraftOutputDTO> getDraft(@PathVariable String id, Authentication authentication)
            throws DomainException {
        return ResponseEntity.ok(getDraftUseCase.execute(new DraftDecisionInputDTO(callerMsrId(authentication), id)));
    }

    @PostMapping("/drafts/{id}/confirm")
    @Operation(summary = "Confirm a draft: its checks run again and, if none fails, the order is placed as the caller")
    public ResponseEntity<OrderDraftOutputDTO> confirm(@PathVariable String id, Authentication authentication)
            throws DomainException {
        return ResponseEntity.ok(confirmDraftUseCase.execute(new DraftDecisionInputDTO(callerMsrId(authentication), id)));
    }

    @PostMapping("/drafts/{id}/discard")
    @Operation(summary = "Discard a draft; nothing is ordered")
    public ResponseEntity<OrderDraftOutputDTO> discard(@PathVariable String id, Authentication authentication)
            throws DomainException {
        return ResponseEntity.ok(discardDraftUseCase.execute(new DraftDecisionInputDTO(callerMsrId(authentication), id)));
    }

    private static String callerMsrId(Authentication authentication) {
        String msrId = authentication.getDetails() instanceof JwtCallerDetails details
                ? details.medicalSalesRepId()
                : null;
        if (msrId == null || msrId.isBlank()) {
            throw new AccessDeniedException("Only a Medical Sales Rep can use the assistant");
        }
        return msrId;
    }

    /**
     * @param conversationId omit to start a new conversation
     * @param visitId        a visit of the caller's the message is about, or omit
     */
    public record AskRequest(
        @Pattern(regexp = UUID_PATTERN) String conversationId,
        @NotBlank @Size(max = AskAssistantUseCase.MAX_TEXT_LENGTH) String text,
        @Pattern(regexp = UUID_PATTERN) String visitId
    ) {}
}
