package com.das.infra.service.order;

import java.math.BigDecimal;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.das.cleanddd.domain.order.ports.IProductStockPort;

/**
 * Infra adapter implementing {@link IProductStockPort} by calling
 * product-catalog-service's mutating stock endpoints. Unlike the
 * active-status-style checks, these endpoints are authenticated (they mutate
 * real inventory), so the calling user's JWT is forwarded through — exactly
 * the same mechanism visit-infra's HttpMedicalSalesRepRepository uses.
 */
@Service
public class ProductStockAdapter implements IProductStockPort {

    private static final Logger log = LoggerFactory.getLogger(ProductStockAdapter.class);
    private static final String CATALOG_BASE_URL = "http://product-catalog-service";

    private final RestTemplate restTemplate;

    public ProductStockAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public StockReservationResult reserve(String productId, int quantity) {
        try {
            HttpEntity<QuantityRequest> request = new HttpEntity<>(new QuantityRequest(quantity), buildHeaders());
            ResponseEntity<ReserveResponse> response = restTemplate.exchange(
                    CATALOG_BASE_URL + "/api/v1/products/{id}/reserve-stock",
                    Objects.requireNonNull(HttpMethod.POST),
                    request,
                    ReserveResponse.class,
                    productId);
            ReserveResponse body = response.getBody();
            if (body == null) {
                return new StockReservationResult(false, 0, null, null);
            }
            return new StockReservationResult(body.reserved(), body.remainingStock(), body.unitPrice(), body.productName());
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound e) {
            // Insufficient stock, product inactive, or product not found — all mean "not reservable".
            log.debug("Stock reservation rejected for product={} qty={}: {}", productId, quantity, e.getMessage());
            return new StockReservationResult(false, 0, null, null);
        } catch (Exception e) {
            log.error("Failed to reserve stock for product={} qty={}: {}", productId, quantity, e.getMessage());
            return new StockReservationResult(false, 0, null, null);
        }
    }

    @Override
    public void release(String productId, int quantity) {
        try {
            HttpEntity<QuantityRequest> request = new HttpEntity<>(new QuantityRequest(quantity), buildHeaders());
            restTemplate.exchange(
                    CATALOG_BASE_URL + "/api/v1/products/{id}/release-stock",
                    Objects.requireNonNull(HttpMethod.POST),
                    request,
                    Void.class,
                    productId);
        } catch (Exception e) {
            // Best-effort compensation — see CreateOrderUseCase/RejectOrderUseCase for the
            // accepted trade-off (this is not a distributed transaction/saga).
            log.error("Failed to release stock for product={} qty={}: {}", productId, quantity, e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = extractAuthorizationHeader();
        if (token != null) {
            headers.set("Authorization", token);
        }
        return headers;
    }

    private String extractAuthorizationHeader() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getHeader("Authorization") : null;
        } catch (Exception e) {
            return null;
        }
    }

    private record QuantityRequest(int quantity) {}
    private record ReserveResponse(boolean reserved, int remainingStock, BigDecimal unitPrice, String productName) {}
}
