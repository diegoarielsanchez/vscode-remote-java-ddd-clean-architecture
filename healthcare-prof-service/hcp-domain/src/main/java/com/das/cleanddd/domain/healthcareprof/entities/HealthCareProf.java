package com.das.cleanddd.domain.healthcareprof.entities;

import java.util.ArrayList;
import java.util.List;

import com.das.cleanddd.domain.shared.PersonJavaBean;
import com.das.cleanddd.domain.shared.UtilsFactory;
import com.das.cleanddd.domain.shared.ValidationUtils;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;


public class HealthCareProf extends PersonJavaBean {

    public static final int MAX_SPECIALTIES = 7;
    public static final String ERROR_MESSAGE_MAX_SPECIALTIES = "Specialties cannot have more than 7 items";

    private final HealthCareProfId _id;
    private final transient HealthCareProfEmail    _email;
    private final transient HealthCareProfActive _active;
    private final transient ValidationUtils _validationUtils;
    private final List<Specialty> specialties;

    public HealthCareProf(HealthCareProfId id
            , HealthCareProfName name
            , HealthCareProfName surname
            , HealthCareProfEmail email
            , HealthCareProfActive active
            , List<Specialty> specialties
        ) 
        {
        this._id      = id == null ? HealthCareProfId.random() : id;
        this._firstName    = name.toString();
        this._lastName = surname.toString();
        this._email   = email == null ? new HealthCareProfEmail(null) : email;
        this._active =  active == null ? new HealthCareProfActive(false) : active;
        this.specialties = specialties == null ? null : List.copyOf(specialties);
        this._validationUtils = (new UtilsFactory()).getValidationUtils();
        this.ensureSpecialtiesMaxSize(this.specialties);
    }

    private void ensureSpecialtiesMaxSize(List<Specialty> specialties) {
        if (specialties != null && specialties.size() > MAX_SPECIALTIES) {
            throw new IllegalArgumentException(ERROR_MESSAGE_MAX_SPECIALTIES);
        }
    }

    public HealthCareProfId id() {
        return this._id;
    }

    public HealthCareProfName getName() {
        return new HealthCareProfName(_firstName) {
        } ;
    }

    public HealthCareProfName getSurname() {
        return new HealthCareProfName(_lastName) {
        } ;
    }

    public HealthCareProfEmail getEmail() {
        return this._email;
    }

    public HealthCareProfActive getActive() {
        return this._active;
    }
    public Boolean isActive() {
        return this._active != null && Boolean.TRUE.equals(this._active.value()) ? Boolean.TRUE : Boolean.FALSE;
    }
    public HealthCareProfId getId() {
        return this._id;
    }
    public static HealthCareProf create(HealthCareProfId id, HealthCareProfName name, HealthCareProfName surname, HealthCareProfEmail email, HealthCareProfActive active, List<Specialty> specialties) {
        return new HealthCareProf(id, name, surname, email, active, specialties);
    }

    public static HealthCareProf create(HealthCareProfName name, HealthCareProfName surname, HealthCareProfEmail email, List<Specialty> specialties) {
        return new HealthCareProf(null, name, surname, email, null, specialties);
    }
 
    public HealthCareProf changeName(HealthCareProfName newName) throws BusinessException {
        return new HealthCareProf(this._id, newName, new HealthCareProfName(this._lastName), this._email, this._active, this.specialties);
    }

    public HealthCareProf changeSurname(HealthCareProfName newSurname) throws BusinessException {
        return new HealthCareProf(this._id, new HealthCareProfName(this._firstName), newSurname, this._email, this._active, this.specialties);
    }

    public HealthCareProf changeEmail(HealthCareProfEmail newEmail) throws BusinessException {
        return new HealthCareProf(this._id, new HealthCareProfName(this._firstName), new HealthCareProfName(this._lastName), newEmail, this._active, this.specialties);
    }

    public HealthCareProf changeSpecialties(List<Specialty> newSpecialties) throws BusinessException {
        if (newSpecialties == null || newSpecialties.isEmpty()) {
            throw new RequiredFieldException("specialties");
        }

        List<Specialty> normalizedSpecialties = List.copyOf(newSpecialties);
        if (this.specialties != null && this.specialties.equals(normalizedSpecialties)) {
            return this;
        }

        return new HealthCareProf(this._id, new HealthCareProfName(this._firstName), new HealthCareProfName(this._lastName), this._email, this._active, normalizedSpecialties);
    }

    public HealthCareProf addSpecialty(Specialty specialty) throws BusinessException {
        if (specialty == null) {
            throw new RequiredFieldException("specialty");
        }

        List<Specialty> updatedSpecialties = this.specialties == null
                ? new ArrayList<>()
                : new ArrayList<>(this.specialties);

        if (updatedSpecialties.contains(specialty)) {
            return this;
        }

        updatedSpecialties.add(specialty);
        return new HealthCareProf(this._id, new HealthCareProfName(this._firstName), new HealthCareProfName(this._lastName), this._email, this._active, updatedSpecialties);
    }

    public HealthCareProf removeSpecialty(Specialty specialty) throws BusinessException {
        if (specialty == null) {
            throw new RequiredFieldException("specialty");
        }
        if (this.specialties == null || this.specialties.isEmpty()) {
            throw new RequiredFieldException("specialties");
        }

        List<Specialty> updatedSpecialties = new ArrayList<>(this.specialties);
        boolean removed = updatedSpecialties.remove(specialty);
        if (!removed) {
            return this;
        }
        if (updatedSpecialties.isEmpty()) {
            throw new RequiredFieldException("specialties");
        }

        return new HealthCareProf(this._id, new HealthCareProfName(this._firstName), new HealthCareProfName(this._lastName), this._email, this._active, updatedSpecialties);
    }

    public List<Specialty> getSpecialties() {
        return this.specialties;
    }

    public void validate() throws BusinessException {
        if(this._validationUtils.isNull(this._id)) throw new RequiredFieldException("id");
        if(this._validationUtils.isNullOrEmpty(this._firstName)) throw new RequiredFieldException("firstName");
        if(this._validationUtils.isNullOrEmpty(this._lastName)) throw new RequiredFieldException("lastName");
        if(this._validationUtils.isNullOrEmpty(this._email.toString())) throw new RequiredFieldException("email");
        if(this.specialties == null || this.specialties.isEmpty()) throw new RequiredFieldException("specialties");
        //if(this.validationUtils.isNull(this.address)) throw new RequiredFieldException("address");
        //this.address.validate();
        }

    public HealthCareProf setActivate() {
        return this._active != null && this._active.value() ? this : new HealthCareProf(this._id, new HealthCareProfName(this._firstName), new HealthCareProfName(this._lastName), this._email, new HealthCareProfActive(true), this.specialties);
    }
    public HealthCareProf setDeactivate() {
        return this._active != null && !this._active.value() ? this : new HealthCareProf(this._id, new HealthCareProfName(this._firstName), new HealthCareProfName(this._lastName), this._email, new HealthCareProfActive(false), this.specialties);
    }
}
