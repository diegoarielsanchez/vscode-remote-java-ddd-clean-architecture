package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.medicalsalesrep.events.MsrActivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrCreatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDeactivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDomainEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrUpdatedEvent;
import com.das.cleanddd.domain.shared.PersonJavaBean;
import com.das.cleanddd.domain.shared.UtilsFactory;
import com.das.cleanddd.domain.shared.ValidationUtils;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;


public class MedicalSalesRep extends PersonJavaBean<MsrDomainEvent> {

    private final MedicalSalesRepId _id;
    private final transient MedicalSalesRepEmail    _email;
    private final transient MedicalSalesRepActive _active;
    private final transient ValidationUtils _validationUtils;

    public MedicalSalesRep(MedicalSalesRepId id, MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email, MedicalSalesRepActive active) {
            this._id      = id == null ? MedicalSalesRepId.random() : id;
            this._firstName    = name.toString();
            this._lastName = surname.toString();
            this._email   = email == null ? new MedicalSalesRepEmail(null) : email;
            this._active =  active == null ? new MedicalSalesRepActive(false) : active;
        this._validationUtils = (new UtilsFactory()).getValidationUtils();
    }


    public MedicalSalesRepId getId() {
        return this._id;
    }

    public MedicalSalesRepName getName() {
        return new MedicalSalesRepName(_firstName) {
        } ;
    }

    public MedicalSalesRepName getSurname() {
        return new MedicalSalesRepName(_lastName) {
        } ;
    }

    public MedicalSalesRepEmail getEmail() {
        return this._email;
    }

    public MedicalSalesRepActive getActive() {
        return this._active;
    }
    public Boolean isActive() {
        return this._active != null && Boolean.TRUE.equals(this._active.value()) ? Boolean.TRUE : Boolean.FALSE;
    }

    public static MedicalSalesRep create(MedicalSalesRepId id, MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email, MedicalSalesRepActive active) {
        MedicalSalesRep medicalSalesRep = new MedicalSalesRep(id, name, surname, email, active);
        medicalSalesRep.record(new MsrCreatedEvent(
                medicalSalesRep.getId().value(),
                medicalSalesRep.getName().value(),
                medicalSalesRep.getSurname().value(),
                medicalSalesRep.getEmail().value(),
                medicalSalesRep.getActive().value()));
        return medicalSalesRep;
    }
 
    public void validate() throws BusinessException {
        if(this._validationUtils.isNull(this._id)) throw new RequiredFieldException("id");
        if(this._validationUtils.isNullOrEmpty(this._firstName)) throw new RequiredFieldException("firstName");
        if(this._validationUtils.isNullOrEmpty(this._lastName)) throw new RequiredFieldException("lastName");
        if(this._validationUtils.isNullOrEmpty(this._email.toString())) throw new RequiredFieldException("email");
        //if(this.validationUtils.isNull(this.address)) throw new RequiredFieldException("address");
        //this.address.validate();
        }


    public MedicalSalesRep setActivate() {
        if (this._active != null && this._active.value()) {
            return this;
        }
        MedicalSalesRep activated = new MedicalSalesRep(this._id, new MedicalSalesRepName(this._firstName), new MedicalSalesRepName(this._lastName), this._email, new MedicalSalesRepActive(true));
        activated.record(new MsrActivatedEvent(
                activated.getId().value(),
                activated.getName().value(),
                activated.getSurname().value(),
                activated.getEmail().value(),
                activated.getActive().value()));
        return activated;
    }
    public MedicalSalesRep setDeactivate() {
        if (this._active != null && !this._active.value()) {
            return this;
        }
        MedicalSalesRep deactivated = new MedicalSalesRep(this._id, new MedicalSalesRepName(this._firstName), new MedicalSalesRepName(this._lastName), this._email, new MedicalSalesRepActive(false));
        deactivated.record(new MsrDeactivatedEvent(
                deactivated.getId().value(),
                deactivated.getName().value(),
                deactivated.getSurname().value(),
                deactivated.getEmail().value(),
                deactivated.getActive().value()));
        return deactivated;
    }

    public MedicalSalesRep withUpdatedDetails(MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email) {
        MedicalSalesRep updated = new MedicalSalesRep(this._id, name, surname, email, this._active);
        updated.record(new MsrUpdatedEvent(
                updated.getId().value(),
                updated.getName().value(),
                updated.getSurname().value(),
                updated.getEmail().value(),
                updated.getActive().value()));
        return updated;
    }

    public MedicalSalesRepId id() {
        return this._id;
    }
}
