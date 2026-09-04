package com.das.order.application;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.das.cleanddd.domain.order.usecases.dtos.ApproveOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.CreateOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.ListOrdersInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderApprovalStatusOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderIDDto;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.services.OrderUseCaseFactory;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("api/v1/orders")
@Tag(name = "Order", description = "API for Medical Sales Rep orders placed against the Pharma Lab product catalog")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    @Autowired
    private final UseCase<CreateOrderInputDTO, OrderOutputDTO> createOrderUseCase;
    private final UseCase<ApproveOrderInputDTO, OrderOutputDTO> approveOrderUseCase;
    private final UseCase<RejectOrderInputDTO, OrderOutputDTO> rejectOrderUseCase;
    private final UseCase<OrderIDDto, OrderOutputDTO> confirmOrderDeliveryUseCase;
    private final UseCase<OrderIDDto, OrderOutputDTO> getOrderByIdUseCase;
    private final UseCase<OrderIDDto, OrderApprovalStatusOutputDTO> getOrderApprovalStatusUseCase;
    private final UseCase<ListOrdersInputDTO, List<OrderOutputDTO>> listOrdersUseCase;

    public OrderController(OrderUseCaseFactory factory) {
        this.createOrderUseCase = factory.getCreateOrderUseCase();
        this.approveOrderUseCase = factory.getApproveOrderUseCase();
        this.rejectOrderUseCase = factory.getRejectOrderUseCase();
        this.confirmOrderDeliveryUseCase = factory.getConfirmOrderDeliveryUseCase();
        this.getOrderByIdUseCase = factory.getGetOrderByIdUseCase();
        this.getOrderApprovalStatusUseCase = factory.getGetOrderApprovalStatusUseCase();
        this.listOrdersUseCase = factory.getListOrdersUseCase();
    }

    @PostMapping("/create")
    @Operation(summary = "Create an order (MSR ordering products from the Pharma Lab) — validates the MSR and reserves stock for every line")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Object> createOrder(@Valid @RequestBody CreateOrderInputDTO inputDTO) throws DomainException {
        return ResponseEntity.status(HttpStatus.CREATED).body(createOrderUseCase.execute(inputDTO));
    }

    /**
     * The approving actor is always taken from the authenticated principal —
     * never from the request body — so a client cannot spoof "approved by X"
     * (OWASP A01 Broken Access Control).
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an order pending approval")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> approveOrder(@PathVariable String id, Authentication authentication) throws DomainException {
        return ResponseEntity.ok(approveOrderUseCase.execute(new ApproveOrderInputDTO(id, authentication.getName())));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject an order pending approval — releases every line's reserved stock")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> rejectOrder(@PathVariable String id, @RequestBody RejectOrderRequest body,
                                               Authentication authentication) throws DomainException {
        return ResponseEntity.ok(rejectOrderUseCase.execute(new RejectOrderInputDTO(id, authentication.getName(), body.reason())));
    }

    @PostMapping("/{id}/confirm-delivery")
    @Operation(summary = "Idempotent APPROVED->DELIVERED confirmation — manual/ops fallback for delivery-service's async event")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> confirmDelivery(@PathVariable String id) throws DomainException {
        return ResponseEntity.ok(confirmOrderDeliveryUseCase.execute(new OrderIDDto(id)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> getOrderByID(@PathVariable String id) throws DomainException {
        return ResponseEntity.ok(getOrderByIdUseCase.execute(new OrderIDDto(id)));
    }

    /**
     * Minimal endpoint for internal service-to-service checks (delivery-service,
     * before creating a Pharma->MSR delivery). Returns only status/boolean — no
     * PII or pricing — intentionally unauthenticated so peer microservices do
     * not need a user JWT. The API Gateway is the external auth boundary.
     */
    @GetMapping("/{id}/approval-status")
    @Operation(summary = "Check if an order is approved (internal use)")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<OrderApprovalStatusOutputDTO> getApprovalStatus(@PathVariable String id) throws DomainException {
        return ResponseEntity.ok(getOrderApprovalStatusUseCase.execute(new OrderIDDto(id)));
    }

    @PostMapping("/list")
    @Operation(summary = "List orders, optionally filtered by Medical Sales Rep")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> listOrders(
        @RequestParam(name = "medicalSalesRepId", required = false, defaultValue = "") String medicalSalesRepId,
        @RequestParam(required = false, defaultValue = "1") int page,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) throws DomainException {
        pageSize = Math.min(pageSize, 100);
        return ResponseEntity.ok(listOrdersUseCase.execute(new ListOrdersInputDTO(medicalSalesRepId, page, pageSize)));
    }

    public record RejectOrderRequest(String reason) {}
}
