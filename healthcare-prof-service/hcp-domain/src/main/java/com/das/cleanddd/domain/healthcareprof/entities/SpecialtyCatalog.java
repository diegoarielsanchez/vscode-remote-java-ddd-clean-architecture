package com.das.cleanddd.domain.healthcareprof.entities;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.das.cleanddd.domain.shared.exceptions.DomainException;

public final class SpecialtyCatalog {

    private static final Map<String, String> PREDEFINED_SPECIALTIES = new LinkedHashMap<>() {{
        put("CARD", "Cardiology");
        put("DERM", "Dermatology");
        put("NEUR", "Neurology");
        put("PED", "Pediatrics");
        put("ORTH", "Orthopedics");
        put("ONCO", "Oncology");
        put("PSYC", "Psychiatry");
        put("ODON", "Odontology");
        put("OPHT", "Ophthalmology");
        put("ENT", "Ear, Nose and Throat");
        put("GAST", "Gastroenterology");
        put("ENDO", "Endocrinology");
        put("RHEU", "Rheumatology");
        put("UROL", "Urology");
        put("GYNE", "Gynecology");
        put("NEPH", "Nephology");
        put("HEM", "Hematology");
        put("IMM", "Immunology");
        put("INF", "Infectious Diseases");
        put("RAD", "Radiology");
        put("ANES", "Anesthesiology");
        put("PATH", "Pathology");
        put("GEN", "General Practice");
    }};

    private SpecialtyCatalog() {
    }

    public static Specialty fromCode(String code) throws DomainException {
        String normalizedCode = normalizeCode(code);
        String name = PREDEFINED_SPECIALTIES.get(normalizedCode);

        if (name == null) {
            throw new DomainException("Invalid specialty code: " + code);
        }

        return new Specialty(normalizedCode, name);
    }

    public static List<Specialty> all() {
        return PREDEFINED_SPECIALTIES.entrySet().stream()
            .map(entry -> new Specialty(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
