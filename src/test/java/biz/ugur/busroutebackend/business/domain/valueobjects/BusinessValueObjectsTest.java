package biz.ugur.busroutebackend.business.domain.valueobjects;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessValueObjectsTest {

    @Nested
    class BusinessNameValidation {

        @Test
        void rejectsNull() {
            assertThrows(BusinessValidationException.class, () -> BusinessName.of(null));
        }

        @Test
        void rejectsTooShort() {
            assertThrows(BusinessValidationException.class, () -> BusinessName.of("x"));
        }

        @Test
        void rejectsTooLong() {
            String longName = "x".repeat(201);
            assertThrows(BusinessValidationException.class, () -> BusinessName.of(longName));
        }

        @Test
        void trimsValue() {
            assertEquals("ab", BusinessName.of("  ab  ").getValue());
        }

        @Test
        void equalityBasedOnValue() {
            assertEquals(BusinessName.of("Shop"), BusinessName.of("Shop"));
        }
    }

    @Nested
    class TaxNumberValidation {

        @Test
        void rejectsNull() {
            assertThrows(BusinessValidationException.class, () -> TaxNumber.of(null));
        }

        @Test
        void rejectsTooShort() {
            assertThrows(BusinessValidationException.class, () -> TaxNumber.of("abc"));
        }

        @Test
        void rejectsTooLong() {
            String too = "1".repeat(33);
            assertThrows(BusinessValidationException.class, () -> TaxNumber.of(too));
        }

        @Test
        void rejectsInvalidChars() {
            assertThrows(BusinessValidationException.class, () -> TaxNumber.of("1234@!"));
        }

        @Test
        void stripsWhitespaceBeforeValidation() {
            TaxNumber t = TaxNumber.of(" 123 4567 ");
            assertEquals("1234567", t.getValue());
        }

        @Test
        void acceptsAlphanumericWithDashes() {
            TaxNumber t = TaxNumber.of("AB-1234");
            assertEquals("AB-1234", t.getValue());
        }
    }

    @Nested
    class BusinessAddressVo {

        @Test
        void emptyReportsEmpty() {
            BusinessAddress empty = BusinessAddress.empty();
            assertTrue(empty.isEmpty());
            assertNull(empty.getCountry());
            assertNull(empty.getCity());
            assertNull(empty.getAddressLine());
        }

        @Test
        void ofTrimsFieldsAndReportsNonEmpty() {
            BusinessAddress addr = BusinessAddress.of("  TM ", " Ashgabat ", " Main 1 ");
            assertFalse(addr.isEmpty());
            assertEquals("TM", addr.getCountry());
            assertEquals("Ashgabat", addr.getCity());
            assertEquals("Main 1", addr.getAddressLine());
        }

        @Test
        void ofBlanksCollapseToNullAndReportEmpty() {
            BusinessAddress addr = BusinessAddress.of("  ", "", null);
            assertTrue(addr.isEmpty());
        }
    }

    @Nested
    class ContactInfoValidation {

        @Test
        void rejectsBlankPhone() {
            assertThrows(BusinessValidationException.class,
                    () -> ContactInfo.of("Name", "  ", null, null));
        }

        @Test
        void rejectsNullPhone() {
            assertThrows(BusinessValidationException.class,
                    () -> ContactInfo.of("Name", null, null, null));
        }

        @Test
        void rejectsPhoneOutsideAllowedChars() {
            assertThrows(BusinessValidationException.class,
                    () -> ContactInfo.of("Name", "not$a%phone", null, null));
        }

        @Test
        void rejectsInvalidEmail() {
            assertThrows(BusinessValidationException.class,
                    () -> ContactInfo.of("Name", "+99365123456", "not-an-email", null));
        }

        @Test
        void acceptsValidEmailAndWebsite() {
            ContactInfo info = ContactInfo.of(" Merdan ", "+993 65 123456", " m@ugur.biz ", " https://ugur.biz ");
            assertEquals("Merdan", info.getContactPerson());
            assertEquals("m@ugur.biz", info.getEmail());
            assertEquals("https://ugur.biz", info.getWebsiteUrl());
        }

        @Test
        void blankEmailAndWebsiteBecomeNull() {
            ContactInfo info = ContactInfo.of(null, "+99365123456", "", "  ");
            assertNull(info.getEmail());
            assertNull(info.getWebsiteUrl());
            assertNull(info.getContactPerson());
        }
    }
}
