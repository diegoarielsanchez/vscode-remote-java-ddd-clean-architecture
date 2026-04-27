package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

import java.util.List;

public record CreateHealthCareProfInputDTO(
  String name,
  String surname,
  String email,
  List<String> specialties
) {}
