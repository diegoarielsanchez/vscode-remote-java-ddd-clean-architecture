package com.das.infra.service.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import com.das.infra.service.visit.UpstreamDirectoryClient.HcpRecord;
import com.das.infra.service.visit.UpstreamDirectoryClient.MsrRecord;

class SnapshotBootstrapServiceTest {

    private UpstreamDirectoryClient upstream;
    private MsrSnapshotJpaRepository msrRepo;
    private HcpSnapshotJpaRepository hcpRepo;
    private SnapshotBootstrapService service;

    @BeforeEach
    void setUp() {
        upstream = mock(UpstreamDirectoryClient.class);
        msrRepo = mock(MsrSnapshotJpaRepository.class);
        hcpRepo = mock(HcpSnapshotJpaRepository.class);
        service = new SnapshotBootstrapService(upstream, msrRepo, hcpRepo);
        when(msrRepo.findById(any())).thenReturn(Optional.empty());
        when(hcpRepo.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void seedsMissingMsrSnapshots() {
        when(upstream.fetchAllMedicalSalesReps())
                .thenReturn(List.of(new MsrRecord("msr-1", "Ana", "Gil", "ana@example.com", true)));

        service.seedMsr();

        ArgumentCaptor<MsrSnapshotEntity> saved = ArgumentCaptor.forClass(MsrSnapshotEntity.class);
        verify(msrRepo).save(saved.capture());
        MsrSnapshotEntity entity = saved.getValue();
        assertThat(entity.getId()).isEqualTo("msr-1");
        assertThat(entity.getName()).isEqualTo("Ana");
        assertThat(entity.getSurname()).isEqualTo("Gil");
        assertThat(entity.getEmail()).isEqualTo("ana@example.com");
        assertThat(entity.getActive()).isTrue();
    }

    @Test
    void keepsASnapshotAnEventAlreadyWrote() {
        when(upstream.fetchAllMedicalSalesReps())
                .thenReturn(List.of(new MsrRecord("msr-1", "Ana", "Gil", "ana@example.com", true)));
        when(msrRepo.findById("msr-1")).thenReturn(Optional.of(new MsrSnapshotEntity()));

        service.seedMsr();

        verify(msrRepo, never()).save(any());
    }

    @Test
    void storesHcpSpecialtiesAsACommaSeparatedListWithoutBlanks() {
        when(upstream.fetchAllHealthCareProfs()).thenReturn(List.of(new HcpRecord(
                "hcp-1", "Luis", "Paz", "luis@example.com", false,
                Arrays.asList("Cardiology", " ", null, "Pediatrics"))));

        service.seedHcp();

        ArgumentCaptor<HcpSnapshotEntity> saved = ArgumentCaptor.forClass(HcpSnapshotEntity.class);
        verify(hcpRepo).save(saved.capture());
        assertThat(saved.getValue().getSpecialties()).isEqualTo("Cardiology,Pediatrics");
        assertThat(saved.getValue().getActive()).isFalse();
    }

    @Test
    void anUnreachableUpstreamDoesNotStopStartup() {
        when(upstream.fetchAllMedicalSalesReps()).thenThrow(new ResourceAccessException("connection refused"));
        when(upstream.fetchAllHealthCareProfs()).thenThrow(new ResourceAccessException("connection refused"));

        service.seedSnapshots();

        verify(msrRepo, never()).save(any());
        verify(hcpRepo, never()).save(any());
    }
}
