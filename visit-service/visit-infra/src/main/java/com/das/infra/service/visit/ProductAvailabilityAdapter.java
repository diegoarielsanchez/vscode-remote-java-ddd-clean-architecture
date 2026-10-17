package com.das.infra.service.visit;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.visit.ports.IProductAvailability;

/**
 * Infra adapter that implements {@link IProductAvailability} by calling product-catalog-service's
 * minimal availability endpoint. Only a boolean is transferred — no price or stock figures — and the
 * endpoint is permitAll() on the catalog side, so no user JWT is forwarded.
 */
@Service
public class ProductAvailabilityAdapter implements IProductAvailability {

    private static final Logger log = LoggerFactory.getLogger(ProductAvailabilityAdapter.class);
    private static final String CATALOG_BASE_URL = "http://product-catalog-service";

    private final RestTemplate restTemplate;

    public ProductAvailabilityAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public Optional<Boolean> isAvailable(String productId, int quantity) {
        if (productId == null || productId.isBlank() || quantity < 1) {
            return Optional.of(false);
        }
        try {
            ResponseEntity<AvailabilityResponse> response = restTemplate.exchange(
                    CATALOG_BASE_URL + "/api/v1/products/{id}/availability?quantity={quantity}",
                    Objects.requireNonNull(HttpMethod.GET),
                    HttpEntity.EMPTY,
                    AvailabilityResponse.class,
                    productId, quantity);
            AvailabilityResponse body = response.getBody();
            return Optional.of(response.getStatusCode().is2xxSuccessful() && body != null && body.available());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.of(false);
        } catch (Exception e) {
            // Suggestions still work without the catalog; the stock flag is just left unknown.
            log.warn("Catalog availability check failed for product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    private record AvailabilityResponse(boolean available) {}
}
