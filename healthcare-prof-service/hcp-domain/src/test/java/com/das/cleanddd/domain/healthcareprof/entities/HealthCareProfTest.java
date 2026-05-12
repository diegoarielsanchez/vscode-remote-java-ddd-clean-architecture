package com.das.cleanddd.domain.healthcareprof.entities;

import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealthCareProf Aggregate Root")
class HealthCareProfTest {

    private HealthCareProfName name;
    private HealthCareProfName surname;
    private HealthCareProfEmail email;
    private HealthCareProfActive activeTrue;
    private List<Specialty> specialties;

    @BeforeEach
    void setUp() {
        name       = new HealthCareProfName("John");
        surname    = new HealthCareProfName("Smith");
        email      = new HealthCareProfEmail("john.smith@hospital.com");
        activeTrue = new HealthCareProfActive(true);
        specialties = List.of(
                new Specialty("CARD", "Cardiology"),
                new Specialty("NEUR", "Neurology"));
    }

    // ── creation ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create with all fields provided")
        void shouldCreateWithAllFields() {
            HealthCareProfId id = HealthCareProfId.random();
            HealthCareProf hcp = new HealthCareProf(id, name, surname, email, activeTrue, specialties);

            assertEquals(id, hcp.getId());
            assertEquals("John", hcp.getName().value());
            assertEquals("Smith", hcp.getSurname().value());
            assertEquals("john.smith@hospital.com", hcp.getEmail().value());
            assertTrue(hcp.isActive());
            assertEquals(2, hcp.getSpecialties().size());
        }

        @Test
        @DisplayName("should generate random id when id is null")
        void shouldGenerateIdWhenNull() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email, activeTrue, specialties);
            assertNotNull(hcp.getId());
        }

        @Test
        @DisplayName("should default active to false when active is null")
        void shouldDefaultActiveToFalse() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email, null, specialties);
            assertFalse(hcp.isActive());
        }

        @Test
        @DisplayName("factory create() without id should assign a random id")
        void factoryCreateWithoutId() {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            assertNotNull(hcp.getId());
            assertFalse(hcp.isActive());
        }

        @Test
        @DisplayName("should throw when specialties exceed maximum of 7")
        void shouldThrowWhenTooManySpecialties() {
            List<Specialty> tooMany = new ArrayList<>();
            tooMany.add(new Specialty("CARD", "Cardiology"));
            tooMany.add(new Specialty("DERM", "Dermatology"));
            tooMany.add(new Specialty("NEUR", "Neurology"));
            tooMany.add(new Specialty("PED",  "Pediatrics"));
            tooMany.add(new Specialty("ORTH", "Orthopedics"));
            tooMany.add(new Specialty("ONCO", "Oncology"));
            tooMany.add(new Specialty("PSYC", "Psychiatry"));
            tooMany.add(new Specialty("ODON", "Odontology"));  // 8th

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> new HealthCareProf(null, name, surname, email, activeTrue, tooMany));
            assertEquals(HealthCareProf.ERROR_MESSAGE_MAX_SPECIALTIES, ex.getMessage());
        }
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validate")
    class Validate {

        @Test
        @DisplayName("should pass validation with all required fields")
        void shouldPassValidation() {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            assertDoesNotThrow(hcp::validate);
        }

        @Test
        @DisplayName("should throw RequiredFieldException when specialties are null")
        void shouldThrowWhenSpecialtiesNull() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email, activeTrue, null);
            assertThrows(RequiredFieldException.class, hcp::validate);
        }

        @Test
        @DisplayName("should throw RequiredFieldException when specialties are empty")
        void shouldThrowWhenSpecialtiesEmpty() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email, activeTrue, List.of());
            assertThrows(RequiredFieldException.class, hcp::validate);
        }
    }

    // ── changeName / changeSurname / changeEmail ──────────────────────────────

    @Nested
    @DisplayName("Mutators (immutable returns)")
    class Mutators {

        @Test
        @DisplayName("changeName should return a new instance with updated name")
        void shouldChangeName() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            HealthCareProf updated = hcp.changeName(new HealthCareProfName("Jane"));

            assertNotSame(hcp, updated);
            assertEquals("Jane", updated.getName().value());
            assertEquals("Smith", updated.getSurname().value()); // unchanged
        }

        @Test
        @DisplayName("changeSurname should return a new instance with updated surname")
        void shouldChangeSurname() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            HealthCareProf updated = hcp.changeSurname(new HealthCareProfName("Doe"));

            assertNotSame(hcp, updated);
            assertEquals("Doe", updated.getSurname().value());
            assertEquals("John", updated.getName().value()); // unchanged
        }

        @Test
        @DisplayName("changeEmail should return a new instance with updated email")
        void shouldChangeEmail() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            HealthCareProfEmail newEmail = new HealthCareProfEmail("new@hospital.com");
            HealthCareProf updated = hcp.changeEmail(newEmail);

            assertNotSame(hcp, updated);
            assertEquals("new@hospital.com", updated.getEmail().value());
        }
    }

    // ── specialty management ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Specialty management")
    class SpecialtyManagement {

        @Test
        @DisplayName("addSpecialty should add a new specialty")
        void shouldAddSpecialty() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            Specialty dermatology = new Specialty("DERM", "Dermatology");
            HealthCareProf updated = hcp.addSpecialty(dermatology);

            assertEquals(3, updated.getSpecialties().size());
            assertTrue(updated.getSpecialties().contains(dermatology));
        }

        @Test
        @DisplayName("addSpecialty with duplicate should return same instance")
        void shouldReturnSameWhenDuplicateAdded() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            Specialty existing = specialties.get(0);
            HealthCareProf result = hcp.addSpecialty(existing);

            assertEquals(hcp.getSpecialties().size(), result.getSpecialties().size());
        }

        @Test
        @DisplayName("addSpecialty with null should throw RequiredFieldException")
        void shouldThrowWhenAddingNullSpecialty() {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            assertThrows(RequiredFieldException.class, () -> hcp.addSpecialty(null));
        }

        @Test
        @DisplayName("removeSpecialty should remove an existing specialty")
        void shouldRemoveSpecialty() throws BusinessException {
            Specialty cardiology = specialties.get(0);
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            HealthCareProf updated = hcp.removeSpecialty(cardiology);

            assertEquals(1, updated.getSpecialties().size());
            assertFalse(updated.getSpecialties().contains(cardiology));
        }

        @Test
        @DisplayName("removeSpecialty last remaining specialty should throw RequiredFieldException")
        void shouldThrowWhenRemovingLastSpecialty() throws BusinessException {
            List<Specialty> singleSpecialty = List.of(new Specialty("CARD", "Cardiology"));
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, singleSpecialty);
            assertThrows(RequiredFieldException.class,
                    () -> hcp.removeSpecialty(singleSpecialty.get(0)));
        }

        @Test
        @DisplayName("removeSpecialty with null should throw RequiredFieldException")
        void shouldThrowWhenRemovingNullSpecialty() {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            assertThrows(RequiredFieldException.class, () -> hcp.removeSpecialty(null));
        }

        @Test
        @DisplayName("changeSpecialties should replace all specialties")
        void shouldChangeSpecialties() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            List<Specialty> newSpecialties = List.of(new Specialty("PED", "Pediatrics"));
            HealthCareProf updated = hcp.changeSpecialties(newSpecialties);

            assertEquals(1, updated.getSpecialties().size());
            assertEquals("PED", updated.getSpecialties().get(0).code());
        }

        @Test
        @DisplayName("changeSpecialties with null should throw RequiredFieldException")
        void shouldThrowWhenChangeSpecialtiesNull() {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            assertThrows(RequiredFieldException.class, () -> hcp.changeSpecialties(null));
        }

        @Test
        @DisplayName("changeSpecialties with same list should return same instance")
        void shouldReturnSameWhenSpecialtiesUnchanged() throws BusinessException {
            HealthCareProf hcp = HealthCareProf.create(name, surname, email, specialties);
            HealthCareProf result = hcp.changeSpecialties(new ArrayList<>(specialties));
            assertSame(hcp, result);
        }
    }

    // ── activation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Activation")
    class Activation {

        @Test
        @DisplayName("setActivate should return active instance when currently inactive")
        void shouldActivateInactiveHcp() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email,
                    new HealthCareProfActive(false), specialties);
            HealthCareProf activated = hcp.setActivate();
            assertTrue(activated.isActive());
        }

        @Test
        @DisplayName("setActivate should return same instance when already active")
        void shouldReturnSameWhenAlreadyActive() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email,
                    new HealthCareProfActive(true), specialties);
            HealthCareProf result = hcp.setActivate();
            assertSame(hcp, result);
        }

        @Test
        @DisplayName("setDeactivate should return inactive instance when currently active")
        void shouldDeactivateActiveHcp() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email,
                    new HealthCareProfActive(true), specialties);
            HealthCareProf deactivated = hcp.setDeactivate();
            assertFalse(deactivated.isActive());
        }

        @Test
        @DisplayName("setDeactivate should return same instance when already inactive")
        void shouldReturnSameWhenAlreadyInactive() {
            HealthCareProf hcp = new HealthCareProf(null, name, surname, email,
                    new HealthCareProfActive(false), specialties);
            HealthCareProf result = hcp.setDeactivate();
            assertSame(hcp, result);
        }
    }
}
