package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

public record HealthCareProfSpecialtyInputDTO(
    String specialtyCode,
    int page,
    int pageSize
) {}
