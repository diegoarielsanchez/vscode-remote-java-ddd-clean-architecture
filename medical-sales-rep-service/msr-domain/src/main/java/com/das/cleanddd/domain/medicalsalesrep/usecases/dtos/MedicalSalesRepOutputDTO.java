package com.das.cleanddd.domain.medicalsalesrep.usecases.dtos;

public record MedicalSalesRepOutputDTO(
  String id,
  String name,
  String surname,
  String email,
  Boolean active
) {}
