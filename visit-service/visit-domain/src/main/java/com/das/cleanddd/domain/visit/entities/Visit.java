package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
//import java.util.LinkedHashSet;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public final class Visit extends AggregateRoot<Object> {

    private VisitId _visitId;
    private VisitDateTime _visitDate;
    private HealthCareProfId _healthCareProfId;
    private TextValueObject _visitComments;
    private MedicalSalesRepId _medicalSalesRepId;
    private Identifier _visitSiteId;
    private AddressValueObject _address;
    private final List<VisitItem> _visitItems = new ArrayList<>();
    //private final Set<ShoppingItem> shoppingItems = new LinkedHashSet<>();
    private final List<LargeFileValueObject> _productPromoAttachments = new ArrayList<>();

    public Visit(VisitId visitId
        , VisitDateTime visitDate
        , HealthCareProfId healthCareProfId
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRepId medicalSalesRepId) throws BusinessValidationException {
        this(visitId, visitDate, healthCareProfId, visitComments, visitSiteId, visitItems, medicalSalesRepId, null);
    }

    /** Address is optional: the visit site address may not be on file. */
    public Visit(VisitId visitId
        , VisitDateTime visitDate
        , HealthCareProfId healthCareProfId
        , TextValueObject visitComments
        , Identifier visitSiteId
        , List<VisitItem> visitItems
        , MedicalSalesRepId medicalSalesRepId
        , AddressValueObject address) throws BusinessValidationException {

        validateVisitDate(visitDate);
        validateRequiredReferences(medicalSalesRepId, healthCareProfId);

        this._visitId           = visitId;
        this._visitDate         = visitDate;
        this._healthCareProfId  = healthCareProfId;
        this._visitComments     = visitComments;
        this._visitSiteId       = visitSiteId;
        this._medicalSalesRepId = medicalSalesRepId;
        this._address           = address;
    }

    /**
     * Updates the mutable business attributes of this Visit, re-applying the same
     * invariants enforced at creation time (visit date window, required references).
     * Keeping validation centralized here ensures the rules cannot be bypassed by
     * whichever use case (create or update) manipulates this aggregate.
     * <p>Does not touch the address — see the overload below to change it.</p>
     */
    public void update(
        VisitDateTime visitDate,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {

        validateVisitDate(visitDate);
        validateRequiredReferences(medicalSalesRepId, healthCareProfId);

        this._visitDate         = visitDate;
        this._healthCareProfId  = healthCareProfId;
        this._visitComments     = visitComments;
        this._visitSiteId       = visitSiteId;
        this._medicalSalesRepId = medicalSalesRepId;
    }

    /** Same as {@link #update} but also replaces the visit site address. */
    public void update(
        VisitDateTime visitDate,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId,
        AddressValueObject address
    ) throws BusinessValidationException {
        update(visitDate, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId);
        this._address = address;
    }

    private static void validateVisitDate(VisitDateTime visitDate) throws BusinessValidationException {
        if (visitDate == null || visitDate.value().isAfter(LocalDateTime.now())) {
            throw new BusinessValidationException("Visit date cannot be later than today.");
        }
        if (visitDate.value().isBefore(LocalDateTime.now().minusMonths(1))) {
            throw new BusinessValidationException("Visit date cannot be more than one month in the past.");
        }
    }

    private static void validateRequiredReferences(
        MedicalSalesRepId medicalSalesRepId,
        HealthCareProfId healthCareProfId
    ) throws BusinessValidationException {
        if (medicalSalesRepId == null) {
            throw new BusinessValidationException("Medical Sales Representative is required.");
        }
        if (healthCareProfId == null) {
            throw new BusinessValidationException("Health Care Professional is required.");
        }
    }

    /**
     * Rehydrates a Visit from persisted/trusted state (e.g. repository reads for
     * get/list use cases) WITHOUT re-running business validation. Business rules
     * such as the visit date window are enforced only when a Visit is created or
     * updated; historical records that already exist must remain readable even if
     * they no longer satisfy today's date-window rule.
     */
    public static Visit reconstruct(
        VisitId visitId,
        VisitDateTime visitDate,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        List<VisitItem> visitItems,
        MedicalSalesRepId medicalSalesRepId
    ) {
        return reconstruct(visitId, visitDate, healthCareProfId, visitComments, visitSiteId, visitItems,
                medicalSalesRepId, null);
    }

    /** Same as {@link #reconstruct} but also rehydrates the visit site address. */
    public static Visit reconstruct(
        VisitId visitId,
        VisitDateTime visitDate,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        List<VisitItem> visitItems,
        MedicalSalesRepId medicalSalesRepId,
        AddressValueObject address
    ) {
        Visit visit = new Visit();
        visit._visitId           = visitId;
        visit._visitDate         = visitDate;
        visit._healthCareProfId  = healthCareProfId;
        visit._visitComments     = visitComments;
        visit._visitSiteId       = visitSiteId;
        visit._medicalSalesRepId = medicalSalesRepId;
        visit._address           = address;
        if (visitItems != null) {
            visit._visitItems.addAll(visitItems);
        }
        return visit;
    }

    private Visit() {
        _visitId           = null;
        _visitDate         = null;
        _healthCareProfId  = null;
        _visitComments     = null;
        _visitSiteId       = null;
        _address           = null;
        _medicalSalesRepId = null;
    }

    public void addItem(VisitItem visitItem) {
        _visitItems.add(visitItem);
    }

    public void removeItem(VisitItem visitItem) {
        _visitItems.remove(visitItem);
    }

    public void addProductPromoAttachment(LargeFileValueObject attachment) {
        if (attachment == null) {
            throw new IllegalArgumentException("Product promo attachment must not be null.");
        }
        _productPromoAttachments.add(attachment);
    }

    public void removeProductPromoAttachment(LargeFileValueObject attachment) {
        _productPromoAttachments.remove(attachment);
    }

    public List<LargeFileValueObject> productPromoAttachments() {
        return List.copyOf(_productPromoAttachments);
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

    public VisitDateTime visitDate() {
        return _visitDate;
    }

    public String visitDayPeriod() {
        if (_visitDate == null) {
            return null;
        }
        return _visitDate.value().getHour() < 12 ? "MORNING" : "AFTERNOON";
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
