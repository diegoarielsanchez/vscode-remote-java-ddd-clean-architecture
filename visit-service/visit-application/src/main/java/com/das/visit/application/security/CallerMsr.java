package com.das.visit.application.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * The Medical Sales Rep the caller acts as — the token's {@code msr_id} claim. Visits and visit plans
 * always belong to the caller's own MSR: a request naming another MSR is refused rather than
 * silently reassigned (OWASP A01).
 */
public final class CallerMsr {

    private CallerMsr() {}

    /** The token's {@code msr_id} claim, or {@code null}. */
    public static String of(Authentication authentication) {
        return authentication.getDetails() instanceof JwtCallerDetails details ? details.medicalSalesRepId() : null;
    }

    /**
     * The caller's own MSR.
     *
     * @param requestedMsrId the request's MSR, if it sent one — it must be the caller's own
     * @param what           what is being managed, for the refusal message (e.g. "visits")
     * @throws AccessDeniedException when the caller has no MSR or the request names another
     */
    public static String require(Authentication authentication, String requestedMsrId, String what) {
        String callerMsrId = of(authentication);
        if (callerMsrId == null) {
            throw new AccessDeniedException("Only a Medical Sales Rep can manage " + what);
        }
        if (requestedMsrId != null && !requestedMsrId.isBlank() && !requestedMsrId.equals(callerMsrId)) {
            throw new AccessDeniedException("Only your own Medical Sales Rep's " + what + " can be managed");
        }
        return callerMsrId;
    }
}
