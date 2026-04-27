package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public final class VisitPlan extends AggregateRoot {

    private VisitId _visitId;
    private LocalDateTime _visitDateTime;
    private HealthCareProf _healthCareProf;
    private TextValueObject _visitComments;
    private MedicalSalesRep _medicalSalesRep;
    private Identifier _visitSiteId;
    private final List<VisitItem> _visitItems = new ArrayList<>();

    public VisitPlan(VisitId visitId
        , LocalDateTime visitDateTime
        , HealthCareProf healthCareProf
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRep medicalSalesRep) throws BusinessValidationException {

        if (visitDateTime == null || visitDateTime.toLocalDate().isBefore(LocalDate.now())) {
            throw new BusinessValidationException("Visit date/time cannot be in the past.");
        }

        this._visitId = visitId;
        this._visitDateTime = visitDateTime;
        this._healthCareProf = healthCareProf;
        this._visitComments = visitComments;
        this._visitSiteId = visitSiteId;
        this._medicalSalesRep = medicalSalesRep;
    }

    @SuppressWarnings("unused")
    private VisitPlan() {
        _visitId = null;
        _visitDateTime = null;
        _healthCareProf = null;
        _visitComments = null;
        _visitSiteId = null;
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

    public LocalDateTime visitTimeDate() {
        return _visitDateTime;
    }

    public String visitDayPeriod() {
        if (_visitDateTime == null) {
            return null;
        }
        return _visitDateTime.getHour() < 12 ? "MORNING" : "AFTERNOON";
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
        VisitPlan visitPlan = (VisitPlan) o;
        return _visitId.equals(visitPlan._visitId) &&
               _visitDateTime.equals(visitPlan._visitDateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_visitId, _visitDateTime);
    }

}
