package com.das.cleanddd.domain.visit.usecases.services;

import java.util.ArrayList;
import java.util.List;

import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;
import com.das.cleanddd.domain.visit.usecases.dtos.AttachmentDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.AttachmentResultDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UploadAttachmentsInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.UploadAttachmentsOutputDTO;

public final class UploadProductPromoAttachmentsUseCase
        implements UseCase<UploadAttachmentsInputDTO, UploadAttachmentsOutputDTO> {

    private final IVisitRepository visitRepository;
    private final IProductPromoAttachmentStorage attachmentStorage;

    public UploadProductPromoAttachmentsUseCase(
            IVisitRepository visitRepository,
            IProductPromoAttachmentStorage attachmentStorage) {
        this.visitRepository = visitRepository;
        this.attachmentStorage = attachmentStorage;
    }

    @Override
    public UploadAttachmentsOutputDTO execute(UploadAttachmentsInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.visitId() == null || inputDTO.visitId().isBlank()) {
            throw new DomainException("Visit ID is required");
        }
        if (inputDTO.attachments() == null || inputDTO.attachments().isEmpty()) {
            throw new DomainException("At least one attachment is required");
        }

        Visit visit = visitRepository.search(new VisitId(inputDTO.visitId()))
                .orElseThrow(() -> new DomainException("Visit not found: " + inputDTO.visitId()));

        List<AttachmentResultDTO> results = new ArrayList<>();

        for (AttachmentDTO dto : inputDTO.attachments()) {
            LargeFileValueObject metadata = LargeFileValueObject.of(
                    dto.fileName(),
                    dto.contentType(),
                    dto.content().length,
                    dto.content()
            );

            String storageRef = attachmentStorage.store(inputDTO.visitId(), metadata, dto.content());
            visit.addProductPromoAttachment(metadata);

            results.add(new AttachmentResultDTO(
                    metadata.fileName(),
                    storageRef,
                    metadata.sha256Hash()
            ));
        }

        visitRepository.save(visit);

        return new UploadAttachmentsOutputDTO(inputDTO.visitId(), results);
    }
}
