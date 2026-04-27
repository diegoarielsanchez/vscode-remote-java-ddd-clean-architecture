package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

public record HealthCareProfNamesInputDTO(
  String name,
  String surname,
  int page,
  int pageSize
) {}
