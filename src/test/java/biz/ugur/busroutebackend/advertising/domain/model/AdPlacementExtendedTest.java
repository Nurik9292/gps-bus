package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementStatusChangedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.PlacementStateTransitionException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdPlacementExtendedTest {

    private AdPlacement newDraft() {
        return AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Promo", "content", "https://img", "https://target", "Click",
                null, List.of("home"), null, 1);
    }

    private AdPlacement approvedDraft() {
        return newDraft().approve("admin-1");
    }

    @Nested
    class PlacementStatusEnum {

        @Test
        void draftCanMoveToPendingPaymentAndCancelled() {
            assertTrue(PlacementStatus.DRAFT.canTransitionTo(PlacementStatus.PENDING_PAYMENT));
            assertTrue(PlacementStatus.DRAFT.canTransitionTo(PlacementStatus.CANCELLED));
            assertFalse(PlacementStatus.DRAFT.canTransitionTo(PlacementStatus.ACTIVE));
            assertFalse(PlacementStatus.DRAFT.canTransitionTo(PlacementStatus.EXPIRED));
        }

        @Test
        void terminalStatusesAllowNoTransitions() {
            for (PlacementStatus target : PlacementStatus.values()) {
                assertFalse(PlacementStatus.EXPIRED.canTransitionTo(target));
                assertFalse(PlacementStatus.CANCELLED.canTransitionTo(target));
            }
        }

        @Test
        void activeOnlyTransitionsToExpiredOrCancelled() {
            assertTrue(PlacementStatus.ACTIVE.canTransitionTo(PlacementStatus.EXPIRED));
            assertTrue(PlacementStatus.ACTIVE.canTransitionTo(PlacementStatus.CANCELLED));
            assertFalse(PlacementStatus.ACTIVE.canTransitionTo(PlacementStatus.DRAFT));
            assertFalse(PlacementStatus.ACTIVE.canTransitionTo(PlacementStatus.SCHEDULED));
        }

        @Test
        void descriptionsArePresentForAllValues() {
            for (PlacementStatus s : PlacementStatus.values()) {
                assertNotNull(s.getDescription());
                assertFalse(s.getDescription().isBlank(), s.name());
            }
        }
    }

    @Nested
    class FullLifecycleTransitions {

        @Test
        void markAsPendingPaymentWithoutApproveStillWorksFromDraft() {
            AdPlacement placement = newDraft().markAsPendingPayment();
            assertEquals(PlacementStatus.PENDING_PAYMENT, placement.getStatus());
            assertEquals(1, placement.getDomainEvents().size());
            assertInstanceOf(AdPlacementStatusChangedEvent.class, placement.getDomainEvents().get(0));
        }

        @Test
        void pendingPaymentToScheduledToActiveToExpired() {
            AdPlacement pending = approvedDraft();
            AdPlacement scheduled = pending.markAsScheduled();
            AdPlacement active = scheduled.markAsActive();
            AdPlacement expired = active.markAsExpired();

            assertEquals(PlacementStatus.SCHEDULED, scheduled.getStatus());
            assertEquals(PlacementStatus.ACTIVE, active.getStatus());
            assertEquals(PlacementStatus.EXPIRED, expired.getStatus());
        }

        @Test
        void cancelFromAnyNonTerminalStateWorks() {
            AdPlacement pending = approvedDraft();
            AdPlacement cancelled = pending.cancel();
            assertEquals(PlacementStatus.CANCELLED, cancelled.getStatus());
        }

        @Test
        void cannotMarkActiveDirectlyFromDraft() {
            assertThrows(PlacementStateTransitionException.class,
                    () -> newDraft().markAsActive());
        }

        @Test
        void cannotCancelExpired() {
            AdPlacement expired = approvedDraft()
                    .markAsScheduled()
                    .markAsActive()
                    .markAsExpired();
            assertThrows(PlacementStateTransitionException.class, expired::cancel);
        }

        @Test
        void cannotExpireNonActive() {
            AdPlacement pending = approvedDraft();
            assertThrows(PlacementStateTransitionException.class, pending::markAsExpired);
        }

        @Test
        void transitionEmitsStatusChangedEventWithOldAndNew() {
            AdPlacement scheduled = approvedDraft().markAsScheduled();
            AdPlacementStatusChangedEvent ev = (AdPlacementStatusChangedEvent)
                    scheduled.getDomainEvents().get(0);
            assertEquals(PlacementStatus.PENDING_PAYMENT, ev.getFrom());
            assertEquals(PlacementStatus.SCHEDULED, ev.getTo());
        }
    }

    @Nested
    class ApprovalSpecifics {

        @Test
        void approveClearsPreviousRejectionFields() {
            AdPlacement draftWithRejection = newDraft().toBuilder()
                    .rejectionReason("old")
                    .rejectedAt(java.time.LocalDateTime.now())
                    .rejectedByAdminId("admin-2")
                    .build();
            AdPlacement approved = draftWithRejection.approve("admin-1");
            assertNull(approved.getRejectionReason());
            assertNull(approved.getRejectedAt());
            assertNull(approved.getRejectedByAdminId());
        }

        @Test
        void rejectWithReasonTrimmed() {
            AdPlacement rejected = newDraft().reject("admin-1", "  reason  ");
            assertEquals("reason", rejected.getRejectionReason());
        }

        @Test
        void rejectFailsWithNullReason() {
            AdPlacement draft = newDraft();
            assertThrows(Exception.class, () -> draft.reject("admin-1", null));
        }
    }

    @Nested
    class RestoreAndCounters {

        @Test
        void restoreRebuildsWithDefaultsForNullCounters() {
            AdPlacement restored = AdPlacement.restore(
                    PlacementId.generate(), BusinessId.generate(), TariffId.generate(),
                    PlacementType.BANNER, null, PlacementStatus.ACTIVE,
                    "Title", "content", null, null, null,
                    PlacementWindow.unscheduled(), null, null,
                    null, null, 0, null, null, null, null, null, null, null, null);
            assertEquals(0L, restored.getImpressionsCount());
            assertEquals(0L, restored.getClicksCount());
            assertEquals(0L, restored.getVersion());
            assertEquals(0, restored.getDomainEvents().size());
        }

        @Test
        void restorePreservesVersion() {
            AdPlacement restored = AdPlacement.restore(
                    PlacementId.generate(), BusinessId.generate(), TariffId.generate(),
                    PlacementType.BANNER, null, PlacementStatus.ACTIVE,
                    "Title", null, null, null, null,
                    PlacementWindow.unscheduled(), 10L, 3L,
                    null, null, 0, null, null, null, null, null, null, null, 7L);
            assertEquals(7L, restored.getVersion());
            assertEquals(10L, restored.getImpressionsCount());
            assertEquals(3L, restored.getClicksCount());
        }

        @Test
        void recordImpressionDoesNotEmitStatusEvent() {
            AdPlacement placement = newDraft();
            AdPlacement bumped = placement.recordImpression();
            assertEquals(0, bumped.getDomainEvents().size());
        }
    }
}
