package com.das.infra.service.assistant.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.das.cleanddd.domain.assistant.ports.HcpView;
import com.das.cleanddd.domain.assistant.ports.IHcpPort;

/** {@link IHcpPort} over healthcare-prof-service's REST API. The HCP's email is never read. */
@Service
public class HcpRestAdapter implements IHcpPort {

    private static final Logger log = LoggerFactory.getLogger(HcpRestAdapter.class);
    private static final String BASE_URL = "http://healthcare-prof-service/api/v1/healthcareprof";
    static final int PAGE_SIZE = 10;

    private final RestTemplate restTemplate;

    public HcpRestAdapter(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    @Override
    public List<HcpView> findHcps(String query, int page) {
        try {
            HcpResponse[] found = restTemplate.exchange(
                    BASE_URL + "/list?lastName={lastName}&page={page}&pageSize={size}",
                    Objects.requireNonNull(HttpMethod.POST), CallerToken.entity(null), HcpResponse[].class,
                    query, page, PAGE_SIZE).getBody();
            return found == null ? List.of() : Arrays.stream(found).map(HcpResponse::toView).toList();
        } catch (RestClientException e) {
            log.warn("HCP search failed: {}", e.getMessage());
            return List.of();
        }
    }

    record HcpResponse(String id, String name, String surname, Boolean active, List<String> specialties) {
        HcpView toView() {
            return new HcpView(id, name, surname, specialties == null ? List.of() : specialties,
                    Boolean.TRUE.equals(active));
        }
    }
}
