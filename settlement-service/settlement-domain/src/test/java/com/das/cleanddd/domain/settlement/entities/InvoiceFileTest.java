package com.das.cleanddd.domain.settlement.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InvoiceFile Value Object")
class InvoiceFileTest {

    private static final String VALID_NAME         = "INV-001.pdf";
    private static final String VALID_CONTENT_TYPE = "application/pdf";
    private static final byte[] VALID_CONTENT      = new byte[]{1, 2, 3, 4, 5};

    // ── Construction ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create with valid arguments and trim whitespace")
        void shouldCreateWithValidArguments() {
            InvoiceFile file = new InvoiceFile("  " + VALID_NAME + "  ", "  " + VALID_CONTENT_TYPE + "  ", VALID_CONTENT);

            assertEquals(VALID_NAME,         file.fileName());
            assertEquals(VALID_CONTENT_TYPE, file.contentType());
            assertArrayEquals(VALID_CONTENT, file.content());
            assertEquals(VALID_CONTENT.length, file.sizeInBytes());
        }

        @Test
        @DisplayName("should throw when file name is null")
        void shouldThrowWhenFileNameNull() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(null, VALID_CONTENT_TYPE, VALID_CONTENT));
            assertTrue(ex.getMessage().toLowerCase().contains("file name is required"));
        }

        @Test
        @DisplayName("should throw when file name is blank")
        void shouldThrowWhenFileNameBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile("   ", VALID_CONTENT_TYPE, VALID_CONTENT));
        }

        @Test
        @DisplayName("should throw when file name has no extension")
        void shouldThrowWhenFileNameHasNoExtension() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile("invoicewithoutextension", VALID_CONTENT_TYPE, VALID_CONTENT));
            assertTrue(ex.getMessage().contains("file extension"));
        }

        @Test
        @DisplayName("should throw when content type is null")
        void shouldThrowWhenContentTypeNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(VALID_NAME, null, VALID_CONTENT));
        }

        @Test
        @DisplayName("should throw when content type is blank")
        void shouldThrowWhenContentTypeBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(VALID_NAME, "   ", VALID_CONTENT));
        }

        @Test
        @DisplayName("should throw when content type is not a valid MIME type (missing '/')")
        void shouldThrowWhenContentTypeNotMime() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(VALID_NAME, "applicationpdf", VALID_CONTENT));
            assertTrue(ex.getMessage().contains("MIME type"));
        }

        @Test
        @DisplayName("should throw when content is null")
        void shouldThrowWhenContentNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, null));
        }

        @Test
        @DisplayName("should throw when content is empty")
        void shouldThrowWhenContentEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, new byte[0]));
        }

        @Test
        @DisplayName("should throw when content exceeds 10 MB")
        void shouldThrowWhenContentExceedsMaxSize() {
            byte[] oversized = new byte[10 * 1024 * 1024 + 1];
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, oversized));
            assertTrue(ex.getMessage().contains("10 MB"));
        }
    }

    // ── Immutability ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("content() should return a defensive copy — external mutation does not affect stored bytes")
        void contentShouldReturnDefensiveCopy() {
            byte[] original = {10, 20, 30};
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, original);

            byte[] retrieved = file.content();
            retrieved[0] = 99; // mutate the returned array

            assertArrayEquals(new byte[]{10, 20, 30}, file.content());
        }

        @Test
        @DisplayName("mutating the constructor argument should not affect stored bytes")
        void constructorArgMutationShouldNotAffectFile() {
            byte[] mutableInput = {10, 20, 30};
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, mutableInput);

            mutableInput[0] = 99; // mutate after construction

            assertEquals(10, file.content()[0]);
        }
    }

    // ── Equality ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("two instances with identical fields should be equal")
        void sameFieldsShouldBeEqual() {
            InvoiceFile a = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            InvoiceFile b = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two instances with different content should not be equal")
        void differentContentShouldNotBeEqual() {
            InvoiceFile a = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, new byte[]{1, 2, 3});
            InvoiceFile b = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, new byte[]{9, 8, 7});
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two instances with different file names should not be equal")
        void differentFileNameShouldNotBeEqual() {
            InvoiceFile a = new InvoiceFile("invoice-a.pdf", VALID_CONTENT_TYPE, VALID_CONTENT);
            InvoiceFile b = new InvoiceFile("invoice-b.pdf", VALID_CONTENT_TYPE, VALID_CONTENT);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two instances with different content types should not be equal")
        void differentContentTypeShouldNotBeEqual() {
            InvoiceFile a = new InvoiceFile(VALID_NAME, "application/pdf",  VALID_CONTENT);
            InvoiceFile b = new InvoiceFile(VALID_NAME, "application/xml",  VALID_CONTENT);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("instance should be equal to itself")
        void shouldBeEqualToItself() {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            assertEquals(file, file);
        }

        @Test
        @DisplayName("instance should not be equal to null")
        void shouldNotBeEqualToNull() {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            assertNotEquals(null, file);
        }
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toString")
    class ToStringRepresentation {

        @Test
        @DisplayName("toString should include class name, file name, content type and size")
        void toStringShouldContainRelevantFields() {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            String str = file.toString();

            assertTrue(str.contains("InvoiceFile"),       "should contain class name");
            assertTrue(str.contains(VALID_NAME),          "should contain file name");
            assertTrue(str.contains(VALID_CONTENT_TYPE),  "should contain content type");
            assertTrue(str.contains(String.valueOf(VALID_CONTENT.length)), "should contain size");
        }

        @Test
        @DisplayName("toString should not expose raw byte content")
        void toStringShouldNotExposeBytes() {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            assertFalse(file.toString().contains(Arrays.toString(VALID_CONTENT)));
        }
    }

    // ── SHA-256 integrity hash ────────────────────────────────────────────────

    @Nested
    @DisplayName("SHA-256 integrity hash")
    class IntegrityHash {

        @Test
        @DisplayName("sha256Hash() should return a 64-character lowercase hex string")
        void hashShouldBe64CharLowercaseHex() {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            String hash = file.sha256Hash();

            assertNotNull(hash);
            assertEquals(64, hash.length(), "SHA-256 hex digest must be 64 characters");
            assertTrue(hash.matches("[0-9a-f]{64}"), "Hash must be lowercase hex");
        }

        @Test
        @DisplayName("sha256Hash() should match independently computed SHA-256 of the content")
        void hashShouldMatchExpectedDigest() throws Exception {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = HexFormat.of().formatHex(digest.digest(VALID_CONTENT));

            assertEquals(expected, file.sha256Hash());
        }

        @Test
        @DisplayName("two files with identical content should produce the same hash")
        void sameContentShouldProduceSameHash() {
            InvoiceFile a = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            InvoiceFile b = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);

            assertEquals(a.sha256Hash(), b.sha256Hash());
        }

        @Test
        @DisplayName("files with different content should produce different hashes")
        void differentContentShouldProduceDifferentHash() {
            InvoiceFile a = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, new byte[]{1, 2, 3});
            InvoiceFile b = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, new byte[]{4, 5, 6});

            assertNotEquals(a.sha256Hash(), b.sha256Hash());
        }

        @Test
        @DisplayName("mutating the constructor argument after construction should not change the hash")
        void mutatingInputShouldNotChangeHash() {
            byte[] mutableInput = {10, 20, 30};
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, mutableInput);
            String hashBefore = file.sha256Hash();

            mutableInput[0] = 99;

            assertEquals(hashBefore, file.sha256Hash());
        }

        @Test
        @DisplayName("sha256Hash() should be included in toString()")
        void hashShouldAppearInToString() {
            InvoiceFile file = new InvoiceFile(VALID_NAME, VALID_CONTENT_TYPE, VALID_CONTENT);
            assertTrue(file.toString().contains(file.sha256Hash()));
        }
    }
}
