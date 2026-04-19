package biz.ugur.busroutebackend.complaint.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComplaintTest {

    private Complaint fresh;

    @BeforeEach
    void setUp() {
        fresh = Complaint.create(ComplaintType.BUS, "Missing stop", "Stop 7 was removed but still shown on map", "client-1");
    }

    @Test
    void createsWithNewStatusAndTrimmedFields() {
        assertNotNull(fresh.getId());
        assertEquals("Missing stop", fresh.getTitle());
        assertEquals(ComplaintStatus.NEW, fresh.getStatus());
        assertEquals(ComplaintType.BUS, fresh.getType());
        assertEquals("client-1", fresh.getClientId());
        assertNull(fresh.getAdminComment());
    }

    @Test
    void createTrimsAllStrings() {
        Complaint c = Complaint.create(ComplaintType.ROUTE, "  Title  ", "  Desc  ", "  client ");
        assertEquals("Title", c.getTitle());
        assertEquals("Desc", c.getDescription());
        assertEquals("client", c.getClientId());
    }

    @Test
    void createRejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> Complaint.create(null, "t", "d", "c"));
    }

    @Test
    void createRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class,
                () -> Complaint.create(ComplaintType.BUS, " ", "d", "c"));
    }

    @Test
    void createRejectsBlankDescription() {
        assertThrows(IllegalArgumentException.class,
                () -> Complaint.create(ComplaintType.BUS, "t", null, "c"));
    }

    @Test
    void createRejectsBlankClientId() {
        assertThrows(IllegalArgumentException.class,
                () -> Complaint.create(ComplaintType.BUS, "t", "d", "   "));
    }

    @Test
    void markInProgressTransitionsStatus() {
        Complaint inProgress = fresh.markInProgress();
        assertEquals(ComplaintStatus.IN_PROGRESS, inProgress.getStatus());
        assertEquals(ComplaintStatus.NEW, fresh.getStatus());
    }

    @Test
    void resolveSetsStatusAndAdminComment() {
        Complaint resolved = fresh.resolve("Fixed in v2.1");
        assertEquals(ComplaintStatus.RESOLVED, resolved.getStatus());
        assertEquals("Fixed in v2.1", resolved.getAdminComment());
    }

    @Test
    void closeSetsStatusAndAdminComment() {
        Complaint closed = fresh.close("Duplicate");
        assertEquals(ComplaintStatus.CLOSED, closed.getStatus());
        assertEquals("Duplicate", closed.getAdminComment());
    }

    @Test
    void lifecycleFromNewToResolved() {
        Complaint progressed = fresh.markInProgress();
        Complaint resolved = progressed.resolve("Fix");
        assertEquals(ComplaintStatus.RESOLVED, resolved.getStatus());
        assertEquals("Fix", resolved.getAdminComment());
    }
}
