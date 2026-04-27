package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

import java.util.List;

public record HealthCareProfOutputDTO(
  String id,
  String name,
  String surname,
  String email,
  Boolean active,
  List<String> specialties
) {}
