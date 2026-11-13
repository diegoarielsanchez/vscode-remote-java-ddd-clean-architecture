package com.das.infra.service.visit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the MSR and HCP lists from their owning services, to seed visit-service's snapshot tables
 * ({@link SnapshotBootstrapService}).
 *
 * <p>Returns the upstream JSON as plain records. It deliberately does not use msr-domain or hcp-domain types:
 * visit-service only keeps a snapshot of a few fields, and depending on another bounded context's domain would tie
 * its build to that service's model.
 *
 * <p>The upstream list endpoints cap a page at {@value #PAGE_SIZE} rows, so every page is read until a short one.
 */
@Component
public class UpstreamDirectoryClient {

    static final int PAGE_SIZE = 100;
    /** Stops a misbehaving upstream (e.g. one ignoring {@code page}) from looping forever. */
    static final int MAX_PAGES = 1_000;

    private static final String MSR_LIST_URL =
            "http://medical-sales-rep-service/api/v1/medicalsalesrep/list?firstName=&lastName=&page={page}&pageSize={pageSize}";
    private static final String HCP_LIST_URL =
            "http://healthcare-prof-service/api/v1/healthcareprof/list?firstName=&lastName=&page={page}&pageSize={pageSize}";

    public record MsrRecord(String id, String name, String surname, String email, Boolean active) {}

    public record HcpRecord(String id, String name, String surname, String email, Boolean active,
                            List<String> specialties) {}

    private final RestTemplate restTemplate;

    public UpstreamDirectoryClient(RestTemplate loadBalancedRestTemplate) {
        this.restTemplate = loadBalancedRestTemplate;
    }

    /** Every MSR known to medical-sales-rep-service. Throws if the service can't be read. */
    public List<MsrRecord> fetchAllMedicalSalesReps() {
        return fetchAll(MSR_LIST_URL, MsrRecord[].class);
    }

    /** Every HCP known to healthcare-prof-service. Throws if the service can't be read. */
    public List<HcpRecord> fetchAllHealthCareProfs() {
        return fetchAll(HCP_LIST_URL, HcpRecord[].class);
    }

    private <R> List<R> fetchAll(String url, Class<R[]> type) {
        HttpEntity<Void> request = new HttpEntity<>(headers());
        List<R> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            ResponseEntity<R[]> response = restTemplate.exchange(url, HttpMethod.POST, request, type, page, PAGE_SIZE);
            R[] body = response.getBody();
            if (body == null) {
                break;
            }
            all.addAll(Arrays.asList(body));
            if (body.length < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String token = currentAuthorizationHeader();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, token);
        }
        return headers;
    }

    /** Forwards the caller's token when there is a request in progress; at startup there is none. */
    private static String currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
