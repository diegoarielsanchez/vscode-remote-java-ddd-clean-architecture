package com.das.infra.service.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.das.infra.service.visit.UpstreamDirectoryClient.HcpRecord;
import com.das.infra.service.visit.UpstreamDirectoryClient.MsrRecord;

class UpstreamDirectoryClientTest {

    private static final String MSR_LIST =
            "http://medical-sales-rep-service/api/v1/medicalsalesrep/list?firstName=&lastName=&page=%d&pageSize=100";
    private static final String HCP_LIST =
            "http://healthcare-prof-service/api/v1/healthcareprof/list?firstName=&lastName=&page=%d&pageSize=100";

    private MockRestServiceServer server;
    private UpstreamDirectoryClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new UpstreamDirectoryClient(restTemplate);
    }

    @Test
    void readsEveryPageUntilAShortOne() {
        expectMsrPage(1, msrJson(0, 100));
        expectMsrPage(2, msrJson(100, 100));
        expectMsrPage(3, msrJson(200, 7));

        List<MsrRecord> all = client.fetchAllMedicalSalesReps();

        assertThat(all).hasSize(207);
        assertThat(all.get(206).id()).isEqualTo("msr-206");
        server.verify();
    }

    @Test
    void anExactlyFullLastPageIsFollowedByOneEmptyRead() {
        expectMsrPage(1, msrJson(0, 100));
        expectMsrPage(2, "[]");

        assertThat(client.fetchAllMedicalSalesReps()).hasSize(100);
        server.verify();
    }

    @Test
    void mapsHcpFieldsIncludingSpecialties() {
        server.expect(requestTo(HCP_LIST.formatted(1))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        [{"id":"hcp-1","name":"Ana","surname":"Gil","email":"ana@example.com","active":true,
                          "specialties":["Cardiology","Pediatrics"]}]
                        """, MediaType.APPLICATION_JSON));

        List<HcpRecord> all = client.fetchAllHealthCareProfs();

        assertThat(all).containsExactly(new HcpRecord("hcp-1", "Ana", "Gil", "ana@example.com", true,
                List.of("Cardiology", "Pediatrics")));
    }

    @Test
    void anUpstreamFailureIsReportedToTheCaller() {
        server.expect(requestTo(MSR_LIST.formatted(1))).andRespond(withServerError());

        assertThatThrownBy(client::fetchAllMedicalSalesReps).isInstanceOf(RestClientException.class);
    }

    private void expectMsrPage(int page, String json) {
        server.expect(requestTo(MSR_LIST.formatted(page))).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String msrJson(int from, int count) {
        return IntStream.range(from, from + count)
                .mapToObj(i -> """
                        {"id":"msr-%d","name":"Name","surname":"Surname","email":"msr%d@example.com","active":true}"""
                        .formatted(i, i))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
