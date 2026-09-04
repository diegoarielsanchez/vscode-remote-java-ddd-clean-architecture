package com.das.cleanddd.domain.order.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.order.entities.IOrderRepository;
import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.usecases.dtos.ListOrdersInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderMapper;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class ListOrdersUseCase implements UseCase<ListOrdersInputDTO, List<OrderOutputDTO>> {

    @Autowired
    private final IOrderRepository repository;
    @Autowired
    private final OrderMapper mapper;

    public ListOrdersUseCase(IOrderRepository repository, OrderMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<OrderOutputDTO> execute(ListOrdersInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            inputDTO = new ListOrdersInputDTO("", 1, 10);
        }
        int page = inputDTO.page() <= 0 ? 1 : inputDTO.page();
        int pageSize = inputDTO.pageSize() <= 0 ? 10 : inputDTO.pageSize();

        List<Order> orders = (inputDTO.medicalSalesRepId() == null || inputDTO.medicalSalesRepId().isBlank())
                ? repository.searchAll()
                : repository.findByMedicalSalesRepId(new MedicalSalesRepId(inputDTO.medicalSalesRepId()), page, pageSize);

        if (orders.isEmpty()) {
            throw new DomainException("No orders found.");
        }
        return mapper.outputFromEntityList(orders);
    }
}
