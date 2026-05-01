package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.events.HcpDeactivatedEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfIDDto;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class DeactivateHealthCareProfUseCase implements UseCaseOnlyInput<HealthCareProfIDDto> {
   @Autowired
    private final IHealthCareProfRepository repository;
    private final IHcpEventPublisher publisher;

    public DeactivateHealthCareProfUseCase(IHealthCareProfRepository repository, IHcpEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public void execute(HealthCareProfIDDto inputDTO) throws DomainException {
        
        if(inputDTO.id()==null) {
            throw new DomainException("Health Care Professional Id is required.");
          }
        HealthCareProfId id = new HealthCareProfId(inputDTO.id());
        Optional<HealthCareProf> entity = repository.findById(id);
        if(!entity.isPresent()) {
            throw new DomainException("Health Care Professional not found.");
        }
        if(Boolean.TRUE.equals(entity.get().isActive())) {
            HealthCareProf deactivated = entity.get().setDeactivate();
            repository.save(deactivated);
            publisher.publish(new HcpDeactivatedEvent(deactivated.getId().toString(), deactivated.isActive()));
          }
    }
}

