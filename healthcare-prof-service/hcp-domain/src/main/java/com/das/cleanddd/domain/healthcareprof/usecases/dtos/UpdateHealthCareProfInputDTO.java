package com.das.cleanddd.domain.healthcareprof.usecases.dtos;

import java.util.List;

public record UpdateHealthCareProfInputDTO(
    String id,
    String name,
    String surname,
    String email,
    List<String> specialties
    ) {    
}
