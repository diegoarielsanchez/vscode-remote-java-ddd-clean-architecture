package com.das.cleanddd.domain.visit.usecases.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

@Service
public final class UpdateVisitUseCase implements UseCase<UpdateVisitInputDTO, VisitOutputDTO> {

    @Autowired
    private final IVisitRepository visitRepository;
    @Autowired
    private final IHealthCareProfRepository healthCareProfRepository;
    @Autowired
    private final IMedicalSalesRepRepository medicalSalesRepRepository;
    @Autowired
    private final VisitMapper mapper;

    public UpdateVisitUseCase(
        IVisitRepository visitRepository,
        IHealthCareProfRepository healthCareProfRepository,
        IMedicalSalesRepRepository medicalSalesRepRepository,
        VisitMapper mapper
    ) {
        this.visitRepository = visitRepository;
        this.healthCareProfRepository = healthCareProfRepository;
        this.medicalSalesRepRepository = medicalSalesRepRepository;
        this.mapper = mapper;
    }

    @Override
    public VisitOutputDTO execute(UpdateVisitInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.id() == null || inputDTO.id().isBlank()) {
            throw new DomainException("Visit id cannot be null or empty");
        }
        if (inputDTO.visitDate() == null) {
            throw new DomainException("Visit date cannot be null");
        }
        LocalDateTime visitDateTime = inputDTO.visitDate().atStartOfDay();
        if (inputDTO.healthCareProfId() == null || inputDTO.healthCareProfId().isBlank()) {
            throw new DomainException("Health Care Professional id cannot be null or empty");
        }
        if (inputDTO.visitSiteId() == null || inputDTO.visitSiteId().isBlank()) {
            throw new DomainException("Visit site id cannot be null or empty");
        }
        if (inputDTO.medicalSalesRepId() == null || inputDTO.medicalSalesRepId().isBlank()) {
            throw new DomainException("Medical Sales Representative id cannot be null or empty");
        }

        try {
            VisitId visitId = new VisitId(inputDTO.id());
            Optional<Visit> existingVisit = visitRepository.search(visitId);
            if (existingVisit.isEmpty()) {
                throw new DomainException("Visit not found.");
            }

            HealthCareProfId healthCareProfId = new HealthCareProfId(inputDTO.healthCareProfId());
            Optional<HealthCareProf> healthCareProf = healthCareProfRepository.findById(healthCareProfId);
            if (healthCareProf.isEmpty()) {
                throw new DomainException("Health Care Professional not found");
            }

            MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
            Optional<MedicalSalesRep> medicalSalesRep = medicalSalesRepRepository.findById(medicalSalesRepId);
            if (medicalSalesRep.isEmpty()) {
                throw new DomainException("Medical Sales Representative not found");
            }

            Identifier visitSiteId = new Identifier(inputDTO.visitSiteId()) {};
            TextValueObject comments = inputDTO.visitComments() == null
                ? null
                : new TextValueObject(inputDTO.visitComments()) {};

            Visit updatedVisit = new Visit(
                visitId,
                visitDateTime,
                healthCareProf.get(),
                comments,
                visitSiteId,
                List.of(),
                medicalSalesRep.get()
            );

            visitRepository.save(updatedVisit);
            return mapper.outputFromEntity(updatedVisit);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}
