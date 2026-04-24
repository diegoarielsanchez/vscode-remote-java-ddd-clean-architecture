package com.das.cleanddd.domain.healthcareprof.entities;

import java.util.List;

import com.das.cleanddd.domain.shared.UtilsFactory;
import com.das.cleanddd.domain.shared.ValidationUtils;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;


//import lombok.AccessLevel;
//import lombok.AllArgsConstructor;
//import lombok.With;

//@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefaultHealthCareProf extends HealthCareProf {

  private final HealthCareProfId id;

  //@With(AccessLevel.PRIVATE)
  //private final String name;

  //@With(AccessLevel.PRIVATE)
  private final transient HealthCareProfEmail _email;

  //@With(AccessLevel.PRIVATE)
  //private final String surname;

  private final transient HealthCareProfActive _active;

  private final transient ValidationUtils _validationUtils;

  protected DefaultHealthCareProf(HealthCareProfId id, HealthCareProfName name, HealthCareProfName surname, HealthCareProfEmail email, List<Specialty> specialties) throws BusinessException {
    super(id, name, surname, email, new HealthCareProfActive(false), specialties);
    
    this._validationUtils = (new UtilsFactory()).getValidationUtils();

    this.id = id != null ? id : HealthCareProfId.random();
    this._firstName    = name.toString();
    this._lastName = surname.toString();
    this._email   = email;
    this._active = new HealthCareProfActive(false);
    this.validate();
  }


  @Override
  public void validate() throws BusinessException {
    if(this._validationUtils.isNull(this.id)) throw new RequiredFieldException("id");
    if(this._validationUtils.isNullOrEmpty(this._firstName)) throw new RequiredFieldException("firstName");
    if(this._validationUtils.isNullOrEmpty(this._lastName)) throw new RequiredFieldException("lastName");
    if(this._validationUtils.isNullOrEmpty(this._email.toString())) throw new RequiredFieldException("email");
    if(this._validationUtils.isNull(this._active)) throw new RequiredFieldException("active");
    if(this.getSpecialties() == null || this.getSpecialties().isEmpty()) throw new RequiredFieldException("specialties");

    //if(this.validationUtils.isNull(this.address)) throw new RequiredFieldException("address");
    //this.address.validate();
  }


  @Override
  public HealthCareProfId getId() {
    return this.id;
  }

  @Override
  public String getFirstName() {
    return  this._firstName;
  }

  @Override
  public String getLastName() {
    return this._lastName;
  }

  @Override
  public HealthCareProfEmail getEmail() {
    return this._email;
  }

  @Override
  public HealthCareProfActive getActive() {
    return this._active;
  }
  
  @Override
  public Boolean isActive() {
    return this._active != null && this._active.value() ? Boolean.TRUE : Boolean.FALSE;
  } 

  @Override
  public List<Specialty> getSpecialties() {
    return super.getSpecialties();
  }
  
  //@Override
/*   public HealthCareProf changeName(String newName) throws BusinessException {
    DefaultHealthCareProf c = this.lastName.equals(newName) ? this : this.withName(newName);
    //DefaultHealthCareProf c = this.lastName.equals(newName) ? this : this.withName(newName);
    c.validate();
    return c;
  }
 */
  //@Override
/*   public HealthCareProf changeEmail(String newEmail) throws BusinessException {
    DefaultHealthCareProf c = this._email.equals(newEmail) ? this : this.withEmail(newEmail);
    c.validate();
    return c;
  }
 */
  /*
  @Override
  public HealthCareProf changeAddress(Address newAddr) throws BusinessException {
    DefaultHealthCareProf c = this.address.equals(newAddr) ? this : this.withAddress(newAddr); 
    c.validate();
    return c;
  }
  */

  //@Override
/*   public HealthCareProf activate() {
    //return this._active.equals(Boolean.TRUE) ? this : this.withActive(true);
    return this._active.equals(Boolean.TRUE) ? this : this._active = new HealthCareProfActive(true);
  }
 */
  //@Override
/*   public HealthCareProf deactivate() {
    return this._active.equals(Boolean.FALSE) ? this : this._active = new HealthCareProfActive(false);
  } */
}
