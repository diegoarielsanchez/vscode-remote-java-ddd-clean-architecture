/*
 A JavaBean is still a POJO but introduces a strict set of rules around how we implement it:

Access levels – our properties are private and we expose getters and setters
Method names – our getters and setters follow the getX and setX convention (in the case of a boolean, isX can be used for a getter)
Default Constructor – a no-argument constructor must be present so an instance can be created without providing arguments, for example during deserialization
Serializable – implementing the Serializable interface allows us to store the state

 */

package com.das.cleanddd.domain.shared;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

public class PersonJavaBean implements Serializable {

    @Serial
    private static final long serialVersionUID = -3760445487636086034L;
    protected String _firstName;
    protected String _lastName;
    private LocalDate _bornDate;

    public  PersonJavaBean() {
    }

    public PersonJavaBean(String firstName, String lastName, LocalDate bornDate) {
        this._firstName = firstName;
        this._lastName = lastName;
        this._bornDate = bornDate;
    }

    public String getFirstName() {
        return _firstName;
    }

    public String getLastName() {
        return _lastName;
    }

    public LocalDate getBornDate() {
        return _bornDate;
    }

    public void setFirstName(String firstName) {
        this._firstName = firstName;
    }

    public void setLastName(String lastName) {
        this._lastName = lastName;
    }

    public void setBornDate(LocalDate bornDate) {
        this._bornDate = bornDate;
    }

    //  additional getters/setters
}
