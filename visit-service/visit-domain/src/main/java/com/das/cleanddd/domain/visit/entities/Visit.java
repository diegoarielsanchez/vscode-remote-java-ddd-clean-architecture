package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
//import java.util.LinkedHashSet;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public final class Visit extends AggregateRoot {

    private VisitId _visitId;
    private LocalDateTime _visitDate;
    private HealthCareProfId _healthCareProfId;
    private TextValueObject _visitComments;
    private MedicalSalesRepId _medicalSalesRepId;
    private Identifier _visitSiteId;
    private final List<VisitItem> _visitItems = new ArrayList<>();
    //private final Set<ShoppingItem> shoppingItems = new LinkedHashSet<>();

    public Visit(VisitId visitId
        , LocalDateTime visitDate
        , HealthCareProfId healthCareProfId
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRepId medicalSalesRepId) throws BusinessValidationException {

        if (visitDate == null || visitDate.isAfter(LocalDateTime.now())) {
            throw new BusinessValidationException("Visit date cannot be later than today.");
        }
        if (visitDate.isBefore(LocalDateTime.now().minusMonths(1))) {
            throw new BusinessValidationException("Visit date cannot be more than one month in the past.");
        }
        if (medicalSalesRepId == null) {
            throw new BusinessValidationException("Medical Sales Representative is required.");
        }
        if (healthCareProfId == null) {
            throw new BusinessValidationException("Health Care Professional is required.");
        }

        this._visitId           = visitId;
        this._visitDate         = visitDate;
        this._healthCareProfId  = healthCareProfId;
        this._visitComments     = visitComments;
        this._visitSiteId       = visitSiteId;
        this._medicalSalesRepId = medicalSalesRepId;
    }

    @SuppressWarnings("unused")
    private Visit() {
        _visitId           = null;
        _visitDate         = null;
        _healthCareProfId  = null;
        _visitComments     = null;
        _visitSiteId       = null;
        _medicalSalesRepId = null;
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

    public HealthCareProfId healthCareProfId() {
        return _healthCareProfId;
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

    public MedicalSalesRepId medicalSalesRepId() {
        return _medicalSalesRepId;
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
