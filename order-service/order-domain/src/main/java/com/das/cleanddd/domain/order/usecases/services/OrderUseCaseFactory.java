package com.das.cleanddd.domain.order.usecases.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.order.ports.IProductStockPort;
import com.das.cleanddd.domain.order.usecases.dtos.ApproveOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.CreateOrderInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.ListOrdersInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderApprovalStatusOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderIDDto;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderInputDTO;
import com.das.cleanddd.domain.shared.UseCase;

@Service
public class OrderUseCaseFactory {

    private final IOrderRepository orderRepository;
    private final OrderMapper orderMapper = new OrderMapper();

    private final CreateOrderUseCase createOrderUseCase;
    private final ApproveOrderUseCase approveOrderUseCase;
    private final RejectOrderUseCase rejectOrderUseCase;
    private final ConfirmOrderDeliveryUseCase confirmOrderDeliveryUseCase;
    private final GetOrderByIdUseCase getOrderByIdUseCase;
    private final GetOrderApprovalStatusUseCase getOrderApprovalStatusUseCase;
    private final ListOrdersUseCase listOrdersUseCase;

    public OrderUseCaseFactory(IOrderRepository orderRepository, IOrderEventPublisher eventPublisher,
                                IMedicalSalesRepValidator medicalSalesRepValidator, IProductStockPort productStockPort) {
        this.orderRepository = orderRepository;
        this.createOrderUseCase = new CreateOrderUseCase(this.orderRepository, this.orderMapper, eventPublisher,
                medicalSalesRepValidator, productStockPort);
        this.approveOrderUseCase = new ApproveOrderUseCase(this.orderRepository, this.orderMapper, eventPublisher);
        this.rejectOrderUseCase = new RejectOrderUseCase(this.orderRepository, this.orderMapper, eventPublisher, productStockPort);
        this.confirmOrderDeliveryUseCase = new ConfirmOrderDeliveryUseCase(this.orderRepository, this.orderMapper, eventPublisher);
        this.getOrderByIdUseCase = new GetOrderByIdUseCase(this.orderRepository, this.orderMapper);
        this.getOrderApprovalStatusUseCase = new GetOrderApprovalStatusUseCase(this.orderRepository);
        this.listOrdersUseCase = new ListOrdersUseCase(this.orderRepository, this.orderMapper);
    }

    public UseCase<CreateOrderInputDTO, OrderOutputDTO> getCreateOrderUseCase() {
        return createOrderUseCase;
    }
    public UseCase<ApproveOrderInputDTO, OrderOutputDTO> getApproveOrderUseCase() {
        return approveOrderUseCase;
    }
    public UseCase<RejectOrderInputDTO, OrderOutputDTO> getRejectOrderUseCase() {
        return rejectOrderUseCase;
    }
    public UseCase<OrderIDDto, OrderOutputDTO> getConfirmOrderDeliveryUseCase() {
        return confirmOrderDeliveryUseCase;
    }
    public UseCase<OrderIDDto, OrderOutputDTO> getGetOrderByIdUseCase() {
        return getOrderByIdUseCase;
    }
    public UseCase<OrderIDDto, OrderApprovalStatusOutputDTO> getGetOrderApprovalStatusUseCase() {
        return getOrderApprovalStatusUseCase;
    }
    public UseCase<ListOrdersInputDTO, List<OrderOutputDTO>> getListOrdersUseCase() {
        return listOrdersUseCase;
    }
}
