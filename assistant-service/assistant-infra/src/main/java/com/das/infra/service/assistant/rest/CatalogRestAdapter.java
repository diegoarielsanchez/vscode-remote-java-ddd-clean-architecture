package com.das.infra.service.assistant.rest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.assistant.ports.ICatalogPort;
import com.das.cleanddd.domain.assistant.ports.ProductView;

/**
 * {@link ICatalogPort} over product-catalog-service's REST API. Fails closed: a product that cannot be read is
 * reported as absent and its stock as unavailable, which fails the draft's checks rather than passing them.
 */
@Service
public class CatalogRestAdapter implements ICatalogPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogRestAdapter.class);
    private static final String BASE_URL = "http://product-catalog-service/api/v1/products";
    static final int PAGE_SIZE = 10;

    private final RestTemplate restTemplate;

    public CatalogRestAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public List<ProductView> findProductsByName(String name, int page) {
        try {
            ProductResponse[] found = restTemplate.exchange(
                    BASE_URL + "/list?name={name}&page={page}&pageSize={size}",
                    Objects.requireNonNull(HttpMethod.POST), CallerToken.entity(null), ProductResponse[].class,
                    name, page, PAGE_SIZE).getBody();
            return found == null ? List.of() : Arrays.stream(found).map(ProductResponse::toView).toList();
        } catch (RestClientException e) {
            log.warn("Product search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<ProductView> findProduct(String productId) {
        try {
            ProductResponse found = restTemplate.exchange(BASE_URL + "/{id}",
                    Objects.requireNonNull(HttpMethod.GET), CallerToken.entity(null), ProductResponse.class,
                    productId).getBody();
            return Optional.ofNullable(found).map(ProductResponse::toView);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Reading product {} failed: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable(String productId, int quantity) {
        try {
            AvailabilityResponse answer = restTemplate.exchange(BASE_URL + "/{id}/availability?quantity={quantity}",
                    Objects.requireNonNull(HttpMethod.GET), CallerToken.entity(null), AvailabilityResponse.class,
                    productId, quantity).getBody();
            return answer != null && answer.available();
        } catch (RestClientException e) {
            log.warn("Availability check for {} failed: {}", productId, e.getMessage());
            return false;
        }
    }

    /** The fields of catalog's {@code ProductOutputDTO} the assistant uses; price and stock are not read. */
    record ProductResponse(String id, String name, String unit, Boolean active, String gtin, boolean lotTracked,
                           StorageResponse storageCondition) {
        ProductView toView() {
            return new ProductView(id, name, unit, gtin, Boolean.TRUE.equals(active), lotTracked,
                    storageCondition == null ? null : storageCondition.toView());
        }
    }

    record StorageResponse(BigDecimal minTemperatureC, BigDecimal maxTemperatureC, Integer maxHumidityPercent,
                           Boolean protectFromLight, Boolean coldChain, String description) {
        ProductView.Storage toView() {
            return new ProductView.Storage(minTemperatureC, maxTemperatureC, maxHumidityPercent, protectFromLight,
                    coldChain, description);
        }
    }

    record AvailabilityResponse(boolean available) {}
}
