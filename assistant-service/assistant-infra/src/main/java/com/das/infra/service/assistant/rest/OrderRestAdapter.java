package com.das.infra.service.assistant.rest;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.assistant.entities.DraftLine;
import com.das.cleanddd.domain.assistant.ports.IOrderPort;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * {@link IOrderPort} over order-service's {@code POST /api/v1/orders/create}, sent with the rep's token: order-service
 * takes the ordering MSR from it and applies its own rules exactly as for an order the rep typed in.
 */
@Service
public class OrderRestAdapter implements IOrderPort {

    private static final Logger log = LoggerFactory.getLogger(OrderRestAdapter.class);
    private static final String CREATE_URL = "http://order-service/api/v1/orders/create";

    private final RestTemplate restTemplate;

    public OrderRestAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public String placeOrder(List<DraftLine> lines) throws DomainException {
        CreateOrderRequest request = new CreateOrderRequest(
                lines.stream().map(l -> new LineRequest(l.productId(), l.quantity())).toList());
        try {
            OrderResponse accepted = restTemplate.exchange(CREATE_URL, Objects.requireNonNull(HttpMethod.POST),
                    CallerToken.entity(request), OrderResponse.class).getBody();
            if (accepted == null || accepted.id() == null) {
                throw new DomainException("order-service accepted the order without returning its id.");
            }
            return accepted.id();
        } catch (HttpClientErrorException e) {
            throw new DomainException("order-service refused the order (" + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            log.error("Placing an order failed: {}", e.getMessage());
            throw new DomainException("order-service could not be reached; the order was not placed.");
        }
    }

    record CreateOrderRequest(List<LineRequest> lines) {}

    record LineRequest(String productId, int quantity) {}

    record OrderResponse(String id, String status) {}
}
