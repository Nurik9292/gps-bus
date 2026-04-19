package biz.ugur.busroutebackend.business.domain.model;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.events.BusinessReactivatedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessUpdatedEvent;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessStateTransitionException;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExtendedTest {

    private Business pending() {
        return Business.create(
                BusinessName.of("TestShop"),
                BusinessType.RESTAURANT,
                ContactInfo.of("Ivan", "+993 65 123456", null, null),
                BusinessAddress.of("Turkmenistan", "Ashgabat", "Main 1"),
                null, null, null, null);
    }

    private Business approved() {
        return pending().approve("admin-1");
    }

    @Nested
    class CreateValidation {

        @Test
        void createRejectsNullName() {
            assertThrows(BusinessValidationException.class, () -> Business.create(
                    null, BusinessType.RESTAURANT,
                    ContactInfo.of("Ivan", "+993 65 123456", null, null),
                    BusinessAddress.of("Turkmenistan", "Ashgabat", "Main 1"),
                    null, null, null, null));
        }

        @Test
        void createRejectsNullType() {
            assertThrows(BusinessValidationException.class, () -> Business.create(
                    BusinessName.of("Shop"), null,
                    ContactInfo.of("Ivan", "+993 65 123456", null, null),
                    BusinessAddress.of("Turkmenistan", "Ashgabat", "Main 1"),
                    null, null, null, null));
        }

        @Test
        void createRejectsNullContactInfo() {
            assertThrows(BusinessValidationException.class, () -> Business.create(
                    BusinessName.of("Shop"), BusinessType.RESTAURANT, null,
                    BusinessAddress.of("Turkmenistan", "Ashgabat", "Main 1"),
                    null, null, null, null));
        }

        @Test
        void createDefaultsAddressWhenNull() {
            Business b = Business.create(
                    BusinessName.of("Shop"), BusinessType.RESTAURANT,
                    ContactInfo.of("Ivan", "+993 65 123456", null, null),
                    null, null, null, null, null);
            assertNotNull(b.getAddress());
        }

        @Test
        void createTrimsOptionalFields() {
            Business b = Business.create(
                    BusinessName.of("Shop"), BusinessType.RESTAURANT,
                    ContactInfo.of("Ivan", "+993 65 123456", null, null),
                    BusinessAddress.of("Turkmenistan", "Ashgabat", "Main 1"),
                    null, "   ", "   ", "   ");
            assertNull(b.getRegistrationNumber());
            assertNull(b.getLogoUrl());
            assertNull(b.getDescription());
        }
    }

    @Nested
    class Transitions {

        @Test
        void cannotApproveAlreadyApproved() {
            assertThrows(BusinessStateTransitionException.class,
                    () -> approved().approve("admin-1"));
        }

        @Test
        void suspendRequiresReason() {
            Business approved = approved();
            assertThrows(BusinessValidationException.class, () -> approved.suspend(""));
            assertThrows(BusinessValidationException.class, () -> approved.suspend(null));
        }

        @Test
        void reactivateFailsFromPending() {
            assertThrows(BusinessStateTransitionException.class, () -> pending().reactivate());
        }

        @Test
        void reactivateFailsFromApproved() {
            assertThrows(BusinessStateTransitionException.class, () -> approved().reactivate());
        }

        @Test
        void reactivateEmitsReactivatedEventAndClearsSuspension() {
            Business suspended = approved().suspend("bad reviews");
            assertNotNull(suspended.getSuspendedAt());
            assertNotNull(suspended.getSuspensionReason());

            Business reactivated = suspended.reactivate();
            assertNull(reactivated.getSuspendedAt());
            assertNull(reactivated.getSuspensionReason());
            assertTrue(reactivated.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof BusinessReactivatedEvent));
        }

        @Test
        void rejectRequiresReason() {
            assertThrows(BusinessValidationException.class, () -> pending().reject(null));
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void updateTracksNameChange() {
            Business updated = pending().updateProfile(
                    BusinessName.of("NewName"), null, null, null, null, null, null, null);
            assertEquals("NewName", updated.getName().getValue());
            BusinessUpdatedEvent ev = updated.getDomainEvents().stream()
                    .filter(e -> e instanceof BusinessUpdatedEvent)
                    .map(e -> (BusinessUpdatedEvent) e)
                    .findFirst().orElseThrow();
            assertTrue(ev.getChanges().containsKey("name"));
        }

        @Test
        void updateWithUnchangedValuesEmitsNoUpdateEvent() {
            Business original = pending();
            Business updated = original.updateProfile(
                    original.getName(), original.getType(),
                    original.getContactInfo(), original.getAddress(),
                    null, null, null, null);
            assertEquals(0, updated.getDomainEvents().stream()
                    .filter(e -> e instanceof BusinessUpdatedEvent)
                    .count());
        }

        @Test
        void updateTracksTypeChange() {
            Business updated = pending().updateProfile(
                    null, BusinessType.HEALTHCARE, null, null, null, null, null, null);
            assertEquals(BusinessType.HEALTHCARE, updated.getType());
        }

        @Test
        void updateTracksTaxNumber() {
            TaxNumber tax = TaxNumber.of("1234567890");
            Business updated = pending().updateProfile(
                    null, null, null, null, tax, null, null, null);
            assertEquals(tax, updated.getTaxNumber());
        }

        @Test
        void updateRejectsRejectedBusiness() {
            Business rejected = pending().reject("no-go");
            assertThrows(BusinessStateTransitionException.class,
                    () -> rejected.updateProfile(BusinessName.of("Try"), null, null, null, null, null, null, null));
        }

        @Test
        void updateClearsDescriptionWhenBlank() {
            Business withDescription = pending().updateProfile(
                    null, null, null, null, null, null, null, "non-empty");
            Business cleared = withDescription.updateProfile(
                    null, null, null, null, null, null, null, "   ");
            assertNull(cleared.getDescription());
        }
    }

    @Nested
    class Restore {

        @Test
        void restoreRebuildsStateWithoutEvents() {
            Business created = pending();
            Business restored = Business.restore(
                    created.getId(), created.getName(), created.getType(),
                    BusinessStatus.APPROVED, null, null,
                    created.getContactInfo(), created.getAddress(),
                    null, null, null, null, "admin",
                    null, null,
                    created.getCreatedAt(), created.getUpdatedAt(), 5L
            );
            assertEquals(BusinessStatus.APPROVED, restored.getStatus());
            assertEquals(5L, restored.getVersion());
            assertEquals(0, restored.getDomainEvents().size());
        }

        @Test
        void restoreDefaultsVersionToZero() {
            Business restored = Business.restore(
                    BusinessId.generate(),
                    BusinessName.of("Shop"), BusinessType.RESTAURANT,
                    BusinessStatus.PENDING, null, null,
                    ContactInfo.of("Ivan", "+993 65 123456", null, null),
                    BusinessAddress.empty(),
                    null, null, null, null, null, null, null, null, null, null);
            assertEquals(0L, restored.getVersion());
        }
    }
}
