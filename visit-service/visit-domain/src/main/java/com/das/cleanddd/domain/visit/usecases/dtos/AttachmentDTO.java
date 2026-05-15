package com.das.cleanddd.domain.visit.usecases.dtos;

public record AttachmentDTO(String fileName, byte[] content, String contentType) {}
