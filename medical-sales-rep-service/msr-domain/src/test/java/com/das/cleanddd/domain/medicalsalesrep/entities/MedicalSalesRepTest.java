package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.AddressValueObject;
import com.das.cleanddd.domain.shared.exceptions.RequiredFieldException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MedicalSalesRep Aggregate Root")
class MedicalSalesRepTest {

    private MedicalSalesRepName name;
    private MedicalSalesRepName surname;
    private MedicalSalesRepEmail email;

    @BeforeEach
    void setUp() {
        name    = new MedicalSalesRepName("John");
        surname = new MedicalSalesRepName("Smith");
        email   = new MedicalSalesRepEmail("john.smith@pharma.com");
    }

    // ── creation ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create with all fields provided")
        void shouldCreateWithAllFields() {
            MedicalSalesRepId id = MedicalSalesRepId.random();
            MedicalSalesRep msr = new MedicalSalesRep(id, name, surname, email, new MedicalSalesRepActive(true));

            assertEquals(id, msr.getId());
            assertEquals("John",  msr.getName().value());
            assertEquals("Smith", msr.getSurname().value());
            assertEquals("john.smith@pharma.com", msr.getEmail().value());
            assertTrue(msr.isActive());
        }

        @Test
        @DisplayName("should generate a random id when id is null")
        void shouldGenerateIdWhenNull() {
            MedicalSalesRep msr = new MedicalSalesRep(null, name, surname, email, new MedicalSalesRepActive(false));
            assertNotNull(msr.getId());
        }

        @Test
        @DisplayName("should default active to false when active is null")
        void shouldDefaultActiveToFalse() {
            MedicalSalesRep msr = new MedicalSalesRep(null, name, surname, email, null);
            assertFalse(msr.isActive());
        }

        @Test
        @DisplayName("static create() should return a MedicalSalesRep with provided id")
        void staticCreateWithId() {
            MedicalSalesRepId id = MedicalSalesRepId.random();
            MedicalSalesRep msr = MedicalSalesRep.create(id, name, surname, email, new MedicalSalesRepActive(false));
            assertEquals(id, msr.getId());
        }
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validate")
    class Validate {

        @Test
        @DisplayName("should pass validation with all required fields")
        void shouldPassValidation() {
            MedicalSalesRep msr = new MedicalSalesRep(MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(false));
            assertDoesNotThrow(msr::validate);
        }

        @Test
        @DisplayName("should pass validation when a valid email is provided")
        void shouldPassValidationWithValidEmail() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname,
                    new MedicalSalesRepEmail("valid@pharma.com"), new MedicalSalesRepActive(false));
            assertDoesNotThrow(msr::validate);
        }
    }

    // ── activation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Activation")
    class Activation {

        @Test
        @DisplayName("setActivate should return an active instance when currently inactive")
        void shouldActivateInactiveMsr() {
            MedicalSalesRep msr = new MedicalSalesRep(MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(false));
            MedicalSalesRep activated = msr.setActivate();
            assertTrue(activated.isActive());
        }

        @Test
        @DisplayName("setActivate should return the same instance when already active")
        void shouldReturnSameWhenAlreadyActive() {
            MedicalSalesRep msr = new MedicalSalesRep(MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true));
            MedicalSalesRep result = msr.setActivate();
            assertSame(msr, result);
        }

        @Test
        @DisplayName("setDeactivate should return an inactive instance when currently active")
        void shouldDeactivateActiveMsr() {
            MedicalSalesRep msr = new MedicalSalesRep(MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true));
            MedicalSalesRep deactivated = msr.setDeactivate();
            assertFalse(deactivated.isActive());
        }

        @Test
        @DisplayName("setDeactivate should return the same instance when already inactive")
        void shouldReturnSameWhenAlreadyInactive() {
            MedicalSalesRep msr = new MedicalSalesRep(MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(false));
            MedicalSalesRep result = msr.setDeactivate();
            assertSame(msr, result);
        }

        @Test
        @DisplayName("setActivate should preserve identity (same id)")
        void shouldPreserveIdOnActivation() {
            MedicalSalesRepId id = MedicalSalesRepId.random();
            MedicalSalesRep msr = new MedicalSalesRep(id, name, surname, email, new MedicalSalesRepActive(false));
            MedicalSalesRep activated = msr.setActivate();
            assertEquals(id, activated.getId());
        }
    }

    // ── factory ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MedicalSalesRepFactory")
    class Factory {

        private MedicalSalesRepFactory factory = new MedicalSalesRepFactory();

        @Test
        @DisplayName("createMedicalSalesRep should produce an inactive MSR with random id")
        void shouldCreateInactiveMsrWithRandomId() throws Exception {
            MedicalSalesRep msr = factory.createMedicalSalesRep(name, surname, email);
            assertNotNull(msr.getId());
            assertFalse(msr.isActive());
        }

        @Test
        @DisplayName("recreateExistingMedicalSalesRepresentative should preserve the provided id")
        void shouldPreserveIdOnRecreate() throws Exception {
            MedicalSalesRepId id = MedicalSalesRepId.random();
            MedicalSalesRep msr = factory.recreateExistingMedicalSalesRepresentative(
                    id, name, surname, email, new MedicalSalesRepActive(true));
            assertEquals(id, msr.getId());
            assertTrue(msr.isActive());
        }

        @Test
        @DisplayName("recreateExistingMedicalSalesRepresentative should throw RequiredFieldException when id is null")
        void shouldThrowWhenIdNullOnRecreate() {
            assertThrows(RequiredFieldException.class,
                    () -> factory.recreateExistingMedicalSalesRepresentative(
                            null, name, surname, email, new MedicalSalesRepActive(false)));
        }
    }

    // ── address ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Address")
    class Address {

        private AddressValueObject address;

        @BeforeEach
        void setUpAddress() {
            address = new AddressValueObject("1 Pharma Way", "Boston", "MA", "02110", "USA");
        }

        @Test
        @DisplayName("should default to no address when omitted")
        void shouldDefaultToNoAddress() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true));

            assertNull(msr.getAddress());
        }

        @Test
        @DisplayName("should expose the address it was constructed with")
        void shouldExposeAddress() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true), address);

            assertEquals(address, msr.getAddress());
        }

        @Test
        @DisplayName("setActivate should preserve the address")
        void activateShouldPreserveAddress() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(false), address);

            MedicalSalesRep activated = msr.setActivate();

            assertEquals(address, activated.getAddress());
        }

        @Test
        @DisplayName("setDeactivate should preserve the address")
        void deactivateShouldPreserveAddress() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true), address);

            MedicalSalesRep deactivated = msr.setDeactivate();

            assertEquals(address, deactivated.getAddress());
        }

        @Test
        @DisplayName("withUpdatedDetails without an address argument should preserve the existing address")
        void withUpdatedDetailsShouldPreserveAddressByDefault() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true), address);

            MedicalSalesRep updated = msr.withUpdatedDetails(name, surname, email);

            assertEquals(address, updated.getAddress());
        }

        @Test
        @DisplayName("withUpdatedDetails should replace the address when a new one is supplied")
        void withUpdatedDetailsShouldReplaceAddress() {
            MedicalSalesRep msr = new MedicalSalesRep(
                    MedicalSalesRepId.random(), name, surname, email, new MedicalSalesRepActive(true), address);
            AddressValueObject newAddress = new AddressValueObject("2 Pharma Way", "Cambridge", "MA", "02139", "USA");

            MedicalSalesRep updated = msr.withUpdatedDetails(name, surname, email, newAddress);

            assertEquals(newAddress, updated.getAddress());
        }
    }
}
