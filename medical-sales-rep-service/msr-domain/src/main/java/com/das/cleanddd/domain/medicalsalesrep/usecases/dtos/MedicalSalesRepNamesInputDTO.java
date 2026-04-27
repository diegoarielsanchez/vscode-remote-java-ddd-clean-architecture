package com.das.cleanddd.domain.medicalsalesrep.usecases.dtos;

public record MedicalSalesRepNamesInputDTO(
  String name,
  String surname,
  int page,
  int pageSize
) {}
