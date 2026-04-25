package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
//import java.util.LinkedHashSet;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public final class Visit extends AggregateRoot {

    private VisitId _visitId;
    private LocalDateTime _visitDate;
    private HealthCareProf _healthCareProf;
    private TextValueObject _visitComments;
    private MedicalSalesRep _medicalSalesRep;
    private Identifier _visitSiteId;
    private final List<VisitItem> _visitItems = new ArrayList<>();
    //private final Set<ShoppingItem> shoppingItems = new LinkedHashSet<>();

    public Visit(VisitId visitId
        , LocalDateTime visitDate
        , HealthCareProf healthCareProf
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRep medicalSalesRep) throws BusinessValidationException {

        if (visitDate == null || visitDate.isAfter(LocalDateTime.now())) {
            throw new BusinessValidationException("Visit date cannot be later than today.");
        }
        if (visitDate.isBefore(LocalDateTime.now().minusMonths(1))) {
            throw new BusinessValidationException("Visit date cannot be more than one month in the past.");
        }
        if (medicalSalesRep == null || !Boolean.TRUE.equals(medicalSalesRep.isActive())) {
            throw new BusinessValidationException("Medical Sales Representative must be active.");
        }
        if (healthCareProf == null || !Boolean.TRUE.equals(healthCareProf.isActive())) {
            throw new BusinessValidationException("Health Care Professional must be active.");
        }

        this._visitId       = visitId;
        this._visitDate = visitDate;
        this._healthCareProf = healthCareProf;
        this._visitComments = visitComments;
        this._visitSiteId = visitSiteId;
        //this._visitItems = visitItems;
        this._medicalSalesRep = medicalSalesRep;
    }

    @SuppressWarnings("unused")
    private Visit() {
        _visitId       = null;
        _visitDate = null;
        _healthCareProf = null;
        _visitComments = null;
        _visitSiteId = null;
        //_visitItems = null;
        _medicalSalesRep = null;
    }

    public void addItem(VisitItem visitItem) {
        _visitItems.add(visitItem);
    }

    public void removeItem(VisitItem visitItem) {
        _visitItems.remove(visitItem);
    }

    public Identifier visitId() {
        return _visitId;
    }

    public HealthCareProf healthCareProf() {
        return _healthCareProf;
    }

    public Identifier visitSideId() {
        return _visitSiteId;
    }

    public TextValueObject visitComments() {
        return _visitComments;
    }

    public LocalDateTime visitDate() {
        return _visitDate;
    }

    public String visitDayPeriod() {
        if (_visitDate == null) {
            return null;
        }
        return _visitDate.getHour() < 12 ? "MORNING" : "AFTERNOON";
    }

    public MedicalSalesRep medicalSalesRep() {
        return _medicalSalesRep;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Visit visit = (Visit) o;
        return _visitId.equals(visit._visitId) &&
               _visitDate.equals(visit._visitDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_visitId, _visitDate);
    }

}
