package com.das.cleanddd.domain.shared;

import java.time.LocalDate;

public class PersonPOJO {
    
    private String _firstName;
    private String _lastName;
    private LocalDate _bornDate;

    public PersonPOJO(String firstName, String lastName, LocalDate bornDate) {
        this._firstName = firstName;
        this._lastName = lastName;
        this._bornDate = bornDate;
    }

    public String name() {
        return this._firstName + " " + this._lastName;
    }

    public LocalDate getStart() {
        return this._bornDate;
    }
}
