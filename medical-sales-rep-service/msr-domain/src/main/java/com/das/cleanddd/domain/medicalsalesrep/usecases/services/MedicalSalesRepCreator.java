package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepActive;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.shared.bus.event.EventBus;

public final class MedicalSalesRepCreator {
    private final IMedicalSalesRepRepository repository;
    //private final EventBus eventBus;

    public MedicalSalesRepCreator(IMedicalSalesRepRepository repository, EventBus eventBus) {
        this.repository = repository;
        //this.eventBus   = eventBus;
    }
    public void create(MedicalSalesRepId id, MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email,MedicalSalesRepActive active) {

        MedicalSalesRep medicalSalesRepresentative = MedicalSalesRep.create(id, name, surname, email, active);
        //eventBus.publish(medicalSalesRepresentative.pullDomainEvents());
        repository.save(medicalSalesRepresentative);

    }
 /*    public void create(CourseId id, CourseName name, CourseDuration duration) {
        Course course = Course.create(id, name, duration);

        repository.save(course);
        eventBus.publish(course.pullDomainEvents());
    } */
}
