package com.das.cleanddd.domain.catalog.usecases.dtos;

public record ProductNamesInputDTO(
  String name,
  int page,
  int pageSize
) {}
