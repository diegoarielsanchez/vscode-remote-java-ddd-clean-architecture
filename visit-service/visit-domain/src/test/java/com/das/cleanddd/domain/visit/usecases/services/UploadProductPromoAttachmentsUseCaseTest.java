package com.das.cleanddd.domain.visit.usecases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import com.das.cleanddd.domain.visit.usecases.dtos.AttachmentDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UploadAttachmentsInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UploadAttachmentsOutputDTO;

@ExtendWith(MockitoExtension.class)
class UploadProductPromoAttachmentsUseCaseTest {

    private static final String VISIT_ID = "723e4567-e89b-12d3-a456-426614174001";
    private static final String HCP_ID   = "723e4567-e89b-12d3-a456-426614174002";
    private static final String MSR_ID   = "723e4567-e89b-12d3-a456-426614174003";
    private static final String SITE_ID  = "723e4567-e89b-12d3-a456-426614174004";

    /** A valid 64-char hex SHA-256 digest for use in stubs. */
    private static final String FAKE_HASH =
        "a".repeat(64);

    @Mock private IVisitRepository             visitRepository;
    @Mock private IProductPromoAttachmentStorage attachmentStorage;

    private UploadProductPromoAttachmentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UploadProductPromoAttachmentsUseCase(visitRepository, attachmentStorage);
    }

    private Visit buildVisit() throws BusinessValidationException {
        return new Visit(
            new VisitId(VISIT_ID),
            LocalDateTime.now().minusDays(1).withHour(10),
            new HealthCareProfId(HCP_ID),
            null,
            new Identifier(SITE_ID) {},
            List.of(),
            new MedicalSalesRepId(MSR_ID)
        );
    }

    private AttachmentDTO pngAttachment() {
        return new AttachmentDTO("promo.png", "image/png", 4L, InputStream.nullInputStream());
    }

    @Nested
    class InputValidation {

        @Test
        void shouldThrowWhenInputIsNull() {
            assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("null");
        }

        @Test
        void shouldThrowWhenVisitIdIsNull() {
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO(null, List.of(pngAttachment()));
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit ID is required");
        }

        @Test
        void shouldThrowWhenVisitIdIsBlank() {
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO("  ", List.of(pngAttachment()));
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit ID is required");
        }

        @Test
        void shouldThrowWhenAttachmentsListIsNull() {
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO(VISIT_ID, null);
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("At least one attachment is required");
        }

        @Test
        void shouldThrowWhenAttachmentsListIsEmpty() {
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO(VISIT_ID, List.of());
            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("At least one attachment is required");
        }
    }

    @Nested
    class BusinessRules {

        @Test
        void shouldThrowWhenVisitNotFound() {
            when(visitRepository.search(any())).thenReturn(Optional.empty());
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO(VISIT_ID, List.of(pngAttachment()));

            assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Visit not found");

            verify(visitRepository, never()).save(any());
        }
    }

    @Nested
    class HappyPath {

        @BeforeEach
        void setUp() throws BusinessValidationException {
            when(visitRepository.search(any())).thenReturn(Optional.of(buildVisit()));
            when(attachmentStorage.store(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(LargeFileValueObject.ofMetadata("promo.png", "image/png", 4L, FAKE_HASH));
        }

        @Test
        void shouldStoreAttachmentAndSaveVisit() throws DomainException {
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO(VISIT_ID, List.of(pngAttachment()));

            UploadAttachmentsOutputDTO output = useCase.execute(input);

            assertThat(output).isNotNull();
            assertThat(output.visitId()).isEqualTo(VISIT_ID);
            assertThat(output.attachments()).hasSize(1);
            assertThat(output.attachments().get(0).fileName()).isEqualTo("promo.png");
            verify(visitRepository).save(any());
        }

        @Test
        void shouldStoreMultipleAttachments() throws DomainException {
            UploadAttachmentsInputDTO input = new UploadAttachmentsInputDTO(
                VISIT_ID,
                List.of(pngAttachment(), pngAttachment())
            );

            UploadAttachmentsOutputDTO output = useCase.execute(input);

            assertThat(output.attachments()).hasSize(2);
            verify(attachmentStorage, org.mockito.Mockito.times(2))
                .store(anyString(), anyString(), anyString(), anyLong(), any());
        }
    }
}
