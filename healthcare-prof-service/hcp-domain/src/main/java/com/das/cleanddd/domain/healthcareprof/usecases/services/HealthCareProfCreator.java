package com.das.cleanddd.domain.healthcareprof.usecases.services;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfActive;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.shared.bus.event.EventBus;

public final class HealthCareProfCreator {
    private final IHealthCareProfRepository repository;
    //private final EventBus eventBus;

    public HealthCareProfCreator(IHealthCareProfRepository repository, EventBus eventBus) {
        this.repository = repository;
        //this.eventBus   = eventBus;
    }
    public void create(HealthCareProfId id, HealthCareProfName name, HealthCareProfName surname, HealthCareProfEmail email,HealthCareProfActive active) {

        HealthCareProf entityActive = HealthCareProf.create(id, name, surname, email, active, null);
        //eventBus.publish(HealthCareProfRepresentative.pullDomainEvents());
        repository.save(entityActive);
    }
}
