package com.das.infra.service.assistant.rest;

import java.time.LocalDateTime;
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

import com.das.cleanddd.domain.assistant.ports.IVisitPort;
import com.das.cleanddd.domain.assistant.ports.VisitView;

/** {@link IVisitPort} over visit-service's REST API. */
@Service
public class VisitRestAdapter implements IVisitPort {

    private static final Logger log = LoggerFactory.getLogger(VisitRestAdapter.class);
    private static final String BASE_URL = "http://visit-service/api/v1/visit";

    private final RestTemplate restTemplate;

    public VisitRestAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public Optional<VisitView> findVisit(String visitId) {
        try {
            VisitResponse found = restTemplate.exchange(BASE_URL + "/{id}",
                    Objects.requireNonNull(HttpMethod.GET), CallerToken.entity(null), VisitResponse.class,
                    visitId).getBody();
            return Optional.ofNullable(found).map(VisitResponse::toView);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Reading visit failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    record VisitResponse(String id, LocalDateTime visitDate, String healthCareProfId, String visitComments,
                         String medicalSalesRepId, List<ItemResponse> items) {
        VisitView toView() {
            return new VisitView(id, visitDate, healthCareProfId, medicalSalesRepId, visitComments,
                    items == null ? List.of() : items.stream().map(i -> new VisitView.Item(i.productId(), i.quantity())).toList());
        }
    }

    record ItemResponse(String productId, int quantity) {}
}
