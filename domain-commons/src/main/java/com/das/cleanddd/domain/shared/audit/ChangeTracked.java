package com.das.cleanddd.domain.shared.audit;

import java.util.List;

/**
 * A domain event that knows what its edit changed, field by field, with the previous values (ALCOA+: original
 * values stay readable after an edit). The audit trail stores these; they are not part of the published event, so
 * fields a service never shares (e.g. an e-mail address) can be included.
 */
public interface ChangeTracked {

    /** The fields whose value changed; empty when the edit changed nothing. */
    List<FieldChange> changes();
}
