package biz.ugur.busroutebackend.business.domain.enums;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessEnumsTest {

    @Nested
    class StatusTransitions {

        @Test
        void pendingCanApproveOrReject() {
            assertTrue(BusinessStatus.PENDING.canTransitionTo(BusinessStatus.APPROVED));
            assertTrue(BusinessStatus.PENDING.canTransitionTo(BusinessStatus.REJECTED));
            assertFalse(BusinessStatus.PENDING.canTransitionTo(BusinessStatus.SUSPENDED));
        }

        @Test
        void approvedCanSuspendOnly() {
            assertTrue(BusinessStatus.APPROVED.canTransitionTo(BusinessStatus.SUSPENDED));
            assertFalse(BusinessStatus.APPROVED.canTransitionTo(BusinessStatus.REJECTED));
            assertFalse(BusinessStatus.APPROVED.canTransitionTo(BusinessStatus.PENDING));
        }

        @Test
        void suspendedCanReactivate() {
            assertTrue(BusinessStatus.SUSPENDED.canTransitionTo(BusinessStatus.APPROVED));
            assertFalse(BusinessStatus.SUSPENDED.canTransitionTo(BusinessStatus.REJECTED));
        }

        @Test
        void rejectedIsTerminal() {
            for (BusinessStatus s : BusinessStatus.values()) {
                assertFalse(BusinessStatus.REJECTED.canTransitionTo(s), s.name());
            }
        }

        @Test
        void isActiveOnlyForApproved() {
            assertTrue(BusinessStatus.APPROVED.isActive());
            assertFalse(BusinessStatus.PENDING.isActive());
            assertFalse(BusinessStatus.SUSPENDED.isActive());
            assertFalse(BusinessStatus.REJECTED.isActive());
        }

        @Test
        void descriptionsPresentForAllValues() {
            for (BusinessStatus s : BusinessStatus.values()) {
                assertNotNull(s.getDescription());
                assertFalse(s.getDescription().isBlank());
            }
        }
    }

    @Nested
    class TypeFrom {

        @Test
        void fromAcceptsExactMatch() {
            assertEquals(BusinessType.RETAIL, BusinessType.from("RETAIL"));
            assertEquals(BusinessType.HEALTHCARE, BusinessType.from("HEALTHCARE"));
        }

        @Test
        void fromIsCaseInsensitiveAndTrims() {
            assertEquals(BusinessType.RESTAURANT, BusinessType.from("  restaurant  "));
            assertEquals(BusinessType.OTHER, BusinessType.from("Other"));
        }

        @Test
        void fromRejectsBlank() {
            assertThrows(BusinessValidationException.class, () -> BusinessType.from(null));
            assertThrows(BusinessValidationException.class, () -> BusinessType.from("  "));
        }

        @Test
        void fromRejectsUnknown() {
            assertThrows(BusinessValidationException.class, () -> BusinessType.from("unknown"));
        }
    }
}
