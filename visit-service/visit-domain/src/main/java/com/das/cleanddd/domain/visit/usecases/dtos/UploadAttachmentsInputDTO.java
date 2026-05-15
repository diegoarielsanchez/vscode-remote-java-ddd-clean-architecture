package com.das.cleanddd.domain.visit.usecases.dtos;

import java.util.List;

public record UploadAttachmentsInputDTO(String visitId, List<AttachmentDTO> attachments) {}
