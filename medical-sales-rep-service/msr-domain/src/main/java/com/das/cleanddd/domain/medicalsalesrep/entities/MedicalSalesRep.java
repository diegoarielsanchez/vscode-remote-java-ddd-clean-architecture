package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.PersonJavaBean;
import com.das.cleanddd.domain.shared.UtilsFactory;
import com.das.cleanddd.domain.shared.ValidationUtils;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;


public class MedicalSalesRep extends PersonJavaBean {

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
        return new MedicalSalesRep(id, name, surname, email, active);
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
        return this._active != null && this._active.value() ? this : new MedicalSalesRep(this._id, new MedicalSalesRepName(this._firstName), new MedicalSalesRepName(this._lastName), this._email, new MedicalSalesRepActive(true));
    }
    public MedicalSalesRep setDeactivate() {
        return this._active != null && !this._active.value() ? this : new MedicalSalesRep(this._id, new MedicalSalesRepName(this._firstName), new MedicalSalesRepName(this._lastName), this._email, new MedicalSalesRepActive(false));
    }


    public MedicalSalesRepId id() {
        return this._id;
    }
}
