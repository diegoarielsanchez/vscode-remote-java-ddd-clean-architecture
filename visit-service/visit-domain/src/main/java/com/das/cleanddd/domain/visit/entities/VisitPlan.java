package com.das.cleanddd.domain.visit.entities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.audit.ChangeReason;
import com.das.cleanddd.domain.shared.audit.FieldChanges;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.events.VisitDomainEvent;
import com.das.cleanddd.domain.visit.events.VisitPlanCreatedEvent;
import com.das.cleanddd.domain.visit.events.VisitPlanUpdatedEvent;

@Service
public final class VisitPlan extends AggregateRoot<VisitDomainEvent> {

    private VisitId _visitId;
    private VisitDateTime _visitDateTime;
    private HealthCareProfId _healthCareProfId;
    private TextValueObject _visitComments;
    private MedicalSalesRepId _medicalSalesRepId;
    private Identifier _visitSiteId;
    private AddressValueObject _address;
    private boolean _active;
    private final List<VisitItem> _visitItems = new ArrayList<>();

    public VisitPlan(VisitId visitId
        , VisitDateTime visitDateTime
        , HealthCareProfId healthCareProfId
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRepId medicalSalesRepId) throws BusinessValidationException {
        this(visitId, visitDateTime, healthCareProfId, visitComments, visitSiteId, visitItems, medicalSalesRepId, true, null);
    }

    public VisitPlan(VisitId visitId
        , VisitDateTime visitDateTime
        , HealthCareProfId healthCareProfId
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRepId medicalSalesRepId
        , boolean active) throws BusinessValidationException {
        this(visitId, visitDateTime, healthCareProfId, visitComments, visitSiteId, visitItems, medicalSalesRepId, active, null);
    }

    /** Address is optional: the visit site address may not be on file. */
    public VisitPlan(VisitId visitId
        , VisitDateTime visitDateTime
        , HealthCareProfId healthCareProfId
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRepId medicalSalesRepId
        , boolean active
        , AddressValueObject address) throws BusinessValidationException {

        if (visitDateTime == null) {
            throw new BusinessValidationException("Visit date/time is required.");
        }
        if (medicalSalesRepId == null) {
            throw new BusinessValidationException("Medical Sales Representative is required.");
        }
        if (healthCareProfId == null) {
            throw new BusinessValidationException("Health Care Professional is required.");
        }

        this._visitId           = visitId;
        this._visitDateTime     = visitDateTime;
        this._healthCareProfId  = healthCareProfId;
        this._visitComments     = visitComments;
        this._visitSiteId       = visitSiteId;
        this._medicalSalesRepId = medicalSalesRepId;
        this._active            = active;
        this._address           = address;
        this._visitItems.addAll(VisitItem.combine(visitItems));
    }

    @SuppressWarnings("unused")
    private VisitPlan() {
        _visitId           = null;
        _visitDateTime     = null;
        _healthCareProfId  = null;
        _visitComments     = null;
        _visitSiteId       = null;
        _medicalSalesRepId = null;
        _address           = null;
        _active            = true;
    }

    public void addItem(VisitItem visitItem) {
        _visitItems.add(visitItem);
    }

    public void removeItem(VisitItem visitItem) {
        _visitItems.remove(visitItem);
    }

    /** Replaces every item: one line per product, repeated products added up. */
    public void replaceItems(List<VisitItem> visitItems) throws BusinessValidationException {
        List<VisitItem> combined = VisitItem.combine(visitItems);
        _visitItems.clear();
        _visitItems.addAll(combined);
    }

    public List<VisitItem> visitItems() {
        return List.copyOf(_visitItems);
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

    public AddressValueObject address() {
        return _address;
    }

    public TextValueObject visitComments() {
        return _visitComments;
    }

    public VisitDateTime visitTimeDate() {
        return _visitDateTime;
    }

    public String visitDayPeriod() {
        if (_visitDateTime == null) {
            return null;
        }
        return _visitDateTime.value().getHour() < 12 ? "MORNING" : "AFTERNOON";
    }

    public MedicalSalesRepId medicalSalesRepId() {
        return _medicalSalesRepId;
    }

    public boolean isActive() {
        return _active;
    }

    public void deactivate() {
        _active = false;
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

    // ── audit trail (ALCOA+) ──────────────────────────────────────────────────

    /**
     * The fields an edit can change, by name, as the audit trail compares them. Take it before an edit and pass it
     * to {@link #recordUpdatedFrom} afterwards.
     */
    public Map<String, Object> auditState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("visitDateTime", _visitDateTime == null ? null : _visitDateTime.value());
        state.put("healthCareProfId", _healthCareProfId == null ? null : _healthCareProfId.value());
        state.put("medicalSalesRepId", _medicalSalesRepId == null ? null : _medicalSalesRepId.value());
        state.put("visitSiteId", _visitSiteId == null ? null : _visitSiteId.value());
        state.put("visitComments", _visitComments == null ? null : _visitComments.value());
        state.put("address", _address);
        state.put("items", VisitItem.describe(_visitItems));
        state.put("active", _active);
        return state;
    }

    /** Records that this visit plan was created. Called once, by {@link VisitPlanFactory}. */
    void recordCreated() {
        record(new VisitPlanCreatedEvent(_visitId.value(), _visitDateTime.value(), _healthCareProfId.value(),
                _medicalSalesRepId.value(), _visitSiteId == null ? null : _visitSiteId.value(),
                _visitComments == null ? null : _visitComments.value(), VisitItem.describe(_visitItems)));
    }

    /** Records an edit without a reason. */
    public void recordUpdatedFrom(Map<String, Object> before) {
        recordUpdatedFrom(before, null);
    }

    /**
     * Records an edit: each field that differs from {@code before} (from {@link #auditState()}), old and new, and
     * {@code reason}, why it was made.
     */
    public void recordUpdatedFrom(Map<String, Object> before, ChangeReason reason) {
        Map<String, Object> after = auditState();
        FieldChanges changes = new FieldChanges();
        before.forEach((field, value) -> changes.compare(field, value, after.get(field)));
        record(new VisitPlanUpdatedEvent(_visitId.value(), changes.toList(), ChangeReason.valueOf(reason)));
    }
}
