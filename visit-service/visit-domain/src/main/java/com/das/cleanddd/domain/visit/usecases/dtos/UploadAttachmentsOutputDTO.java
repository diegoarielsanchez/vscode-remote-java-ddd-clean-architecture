package com.das.cleanddd.domain.visit.usecases.dtos;

import java.util.List;

public record UploadAttachmentsOutputDTO(String visitId, List<AttachmentResultDTO> attachments) {}
