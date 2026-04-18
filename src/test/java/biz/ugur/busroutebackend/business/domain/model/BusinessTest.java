package biz.ugur.busroutebackend.business.domain.model;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.events.BusinessApprovedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessCreatedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessRejectedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessSuspendedEvent;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessStateTransitionException;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessTest {

    @Test
    @DisplayName("create produces PENDING business and emits BusinessCreatedEvent")
    void createEmitsCreatedEvent() {
        Business b = pending();
        assertEquals(BusinessStatus.PENDING, b.getStatus());
        assertNotNull(b.getId());
        assertEquals(1, b.getDomainEvents().size());
        assertInstanceOf(BusinessCreatedEvent.class, b.getDomainEvents().get(0));
    }

    @Test
    @DisplayName("approve PENDING → APPROVED, stamps admin + timestamp, emits event")
    void approveFromPending() {
        Business b = pending().approve("admin-1");
        assertEquals(BusinessStatus.APPROVED, b.getStatus());
        assertEquals("admin-1", b.getApprovedByAdminId());
        assertNotNull(b.getApprovedAt());
        assertTrue(b.getDomainEvents().stream()
                .anyMatch(e -> e instanceof BusinessApprovedEvent));
    }

    @Test
    @DisplayName("approve requires admin id")
    void approveRequiresAdmin() {
        assertThrows(BusinessValidationException.class, () -> pending().approve(""));
        assertThrows(BusinessValidationException.class, () -> pending().approve(null));
    }

    @Test
    @DisplayName("reject requires a reason and is terminal")
    void rejectIsTerminal() {
        Business rejected = pending().reject("Does not meet compliance");
        assertEquals(BusinessStatus.REJECTED, rejected.getStatus());
        assertTrue(rejected.getDomainEvents().stream()
                .anyMatch(e -> e instanceof BusinessRejectedEvent));
        assertThrows(BusinessStateTransitionException.class,
                () -> rejected.approve("admin-1"));
        assertThrows(BusinessValidationException.class, () -> pending().reject(""));
    }

    @Test
    @DisplayName("suspend APPROVED → SUSPENDED → reactivate back to APPROVED")
    void suspendAndReactivate() {
        Business approved = pending().approve("admin");
        Business suspended = approved.suspend("Complaints received");
        assertEquals(BusinessStatus.SUSPENDED, suspended.getStatus());
        assertTrue(suspended.getDomainEvents().stream()
                .anyMatch(e -> e instanceof BusinessSuspendedEvent));

        Business reactivated = suspended.reactivate();
        assertEquals(BusinessStatus.APPROVED, reactivated.getStatus());
    }

    @Test
    @DisplayName("cannot suspend a PENDING business")
    void suspendFromPendingIsIllegal() {
        assertThrows(BusinessStateTransitionException.class,
                () -> pending().suspend("reason"));
    }

    @Test
    @DisplayName("BusinessName enforces length bounds")
    void businessNameValidation() {
        assertThrows(BusinessValidationException.class, () -> BusinessName.of(""));
        assertThrows(BusinessValidationException.class, () -> BusinessName.of("a"));
        assertThrows(BusinessValidationException.class,
                () -> BusinessName.of("x".repeat(201)));
    }

    @Test
    @DisplayName("ContactInfo validates phone format")
    void contactInfoValidation() {
        assertThrows(BusinessValidationException.class,
                () -> ContactInfo.of("Ivan", "abc", null, null));
        assertThrows(BusinessValidationException.class,
                () -> ContactInfo.of("Ivan", "", null, null));
        ContactInfo ok = ContactInfo.of("Ivan", "+993 65 123456", null, null);
        assertEquals("+993 65 123456", ok.getPhone());
    }

    private static Business pending() {
        return Business.create(
                BusinessName.of("TestShop"),
                BusinessType.RESTAURANT,
                ContactInfo.of("Ivan", "+993 65 123456", null, null),
                BusinessAddress.of("Turkmenistan", "Ashgabat", "Main 1"),
                null, null, null, null);
    }
}
