package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.UtilsFactory;
import com.das.cleanddd.domain.shared.ValidationUtils;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;


//import lombok.AccessLevel;
//import lombok.AllArgsConstructor;
//import lombok.With;

//@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefaultMedicalSalesRep extends MedicalSalesRep {

  private final MedicalSalesRepId id;

  //@With(AccessLevel.PRIVATE)
  //private final String name;

  //@With(AccessLevel.PRIVATE)
  private final transient MedicalSalesRepEmail email;

  //@With(AccessLevel.PRIVATE)
  //private final String surname;

  private final transient MedicalSalesRepActive active;

  private final transient ValidationUtils validationUtils;

  protected DefaultMedicalSalesRep(MedicalSalesRepId id, MedicalSalesRepName name, MedicalSalesRepName surname, MedicalSalesRepEmail email) throws BusinessException {
    super(id, name, surname, email, new MedicalSalesRepActive(false));
    
    this.validationUtils = (new UtilsFactory()).getValidationUtils();

    this.id = id != null ? id : MedicalSalesRepId.random();
    this._firstName    = name.toString();
    this._lastName = surname.toString();
    this.email   = email;
    this.active = new MedicalSalesRepActive(false);
    this.validate();
  }


  @Override
  public void validate() throws BusinessException {
    if(this.validationUtils.isNull(this.id)) throw new RequiredFieldException("id");
    if(this.validationUtils.isNullOrEmpty(this._firstName)) throw new RequiredFieldException("firstName");
    if(this.validationUtils.isNullOrEmpty(this._lastName)) throw new RequiredFieldException("lastName");
    if(this.validationUtils.isNullOrEmpty(this.email.toString())) throw new RequiredFieldException("email");
    if(this.validationUtils.isNull(this.active)) throw new RequiredFieldException("active");

    //if(this.validationUtils.isNull(this.address)) throw new RequiredFieldException("address");
    //this.address.validate();
  }


  @Override
  public MedicalSalesRepId getId() {
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
  public MedicalSalesRepEmail getEmail() {
    return this.email;
  }

  @Override
  public MedicalSalesRepActive getActive() {
    return this.active;
  }
  
  
  //@Override
/*   public MedicalSalesRep changeName(String newName) throws BusinessException {
    DefaultMedicalSalesRep c = this.lastName.equals(newName) ? this : this.withName(newName);
    //DefaultMedicalSalesRep c = this.lastName.equals(newName) ? this : this.withName(newName);
    c.validate();
    return c;
  }
 */
  //@Override
/*   public MedicalSalesRep changeEmail(String newEmail) throws BusinessException {
    DefaultMedicalSalesRep c = this.email.equals(newEmail) ? this : this.withEmail(newEmail);
    c.validate();
    return c;
  }
 */
  /*
  @Override
  public MedicalSalesRep changeAddress(Address newAddr) throws BusinessException {
    DefaultMedicalSalesRepve c = this.address.equals(newAddr) ? this : this.withAddress(newAddr); 
    c.validate();
    return c;
  }
  */

  //@Override
/*   public MedicalSalesRep activate() {
    //return this.active.equals(Boolean.TRUE) ? this : this.withActive(true);
    return this.active.equals(Boolean.TRUE) ? this : this.activate = true;
  }
 */
  //@Override
/*   public MedicalSalesRep deactivate() {
    return this.active.equals(Boolean.FALSE) ? this : this.withActive(false);
  } */
}
