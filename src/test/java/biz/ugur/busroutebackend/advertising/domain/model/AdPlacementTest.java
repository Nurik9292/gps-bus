package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementApprovedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementCreatedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementRejectedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementStatusChangedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.exceptions.PlacementStateTransitionException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdPlacementTest {

    private AdPlacement newDraft() {
        return AdPlacement.create(
                BusinessId.generate(),
                TariffId.generate(),
                PlacementType.BANNER,
                null,
                "Promo",
                "content",
                "https://img",
                "https://target",
                "Click",
                null,
                List.of("home"),
                null,
                1
        );
    }

    @Nested
    class Create {

        @Test
        void createsDraftWithZeroCountersAndRaisesCreatedEvent() {
            AdPlacement placement = newDraft();

            assertThat(placement.getStatus()).isEqualTo(PlacementStatus.DRAFT);
            assertThat(placement.getImpressionsCount()).isZero();
            assertThat(placement.getClicksCount()).isZero();
            assertThat(placement.getVersion()).isZero();
            assertThat(placement.getDomainEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(AdPlacementCreatedEvent.class);
        }

        @Test
        void trimsTitleAndDefaultsWindowToUnscheduled() {
            AdPlacement placement = AdPlacement.create(
                    BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                    null, "  Promo  ", null, null, null, null, null, null, null, null);

            assertThat(placement.getTitle()).isEqualTo("Promo");
            assertThat(placement.getWindow()).isEqualTo(PlacementWindow.unscheduled());
            assertThat(placement.getDisplayOrder()).isZero();
        }

        @Test
        void rejectsNullBusinessId() {
            assertThatThrownBy(() -> AdPlacement.create(
                    null, TariffId.generate(), PlacementType.BANNER,
                    null, "t", null, null, null, null, null, null, null, null))
                    .isInstanceOf(AdvertisingValidationException.class);
        }

        @Test
        void rejectsBlankTitle() {
            assertThatThrownBy(() -> AdPlacement.create(
                    BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                    null, "   ", null, null, null, null, null, null, null, null))
                    .isInstanceOf(AdvertisingValidationException.class);
        }

        @Test
        void kindDefaultsToCommercialWhenNotSpecified() {
            AdPlacement placement = newDraft();
            assertThat(placement.getKind()).isEqualTo(PlacementKind.COMMERCIAL);
        }

        @Test
        void kindCanBeSetExplicitly() {
            AdPlacement placement = AdPlacement.create(
                    BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                    PlacementKind.EDITORIAL, "Editorial promo", null, null, null, null,
                    null, null, null, null);
            assertThat(placement.getKind()).isEqualTo(PlacementKind.EDITORIAL);
        }

        @Test
        void targetsDefaultToEmptyImmutableList() {
            AdPlacement placement = newDraft();
            assertThat(placement.getTargets()).isEmpty();
            assertThatThrownBy(() -> placement.getTargets().add(PlacementTarget.general(TargetType.HOME)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void acceptsMixOfGeneralAndSpecificTargets() {
            AdPlacement placement = AdPlacement.create(
                    BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                    PlacementKind.COMMERCIAL, "Promo", null, null, null, null, null, null,
                    List.of(
                            PlacementTarget.general(TargetType.HOME),
                            PlacementTarget.specific(TargetType.ROUTE, "route-14")
                    ), null);
            assertThat(placement.getTargets()).hasSize(2);
            assertThat(placement.getTargets().get(0).getTargetType()).isEqualTo(TargetType.HOME);
            assertThat(placement.getTargets().get(1).getTargetId()).isEqualTo("route-14");
        }

        @Test
        void createdEventCarriesKind() {
            AdPlacement placement = AdPlacement.create(
                    BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                    PlacementKind.EDITORIAL, "Editorial", null, null, null, null, null, null, null, null);
            AdPlacementCreatedEvent event = (AdPlacementCreatedEvent) placement.getDomainEvents().get(0);
            assertThat(event.getKind()).isEqualTo(PlacementKind.EDITORIAL);
        }
    }

    @Nested
    class Approve {

        @Test
        void movesFromDraftToPendingPaymentAndEmitsTwoEvents() {
            AdPlacement approved = newDraft().approve("admin-1");

            assertThat(approved.getStatus()).isEqualTo(PlacementStatus.PENDING_PAYMENT);
            assertThat(approved.getApprovedByAdminId()).isEqualTo("admin-1");
            assertThat(approved.getApprovedAt()).isNotNull();
            assertThat(approved.getDomainEvents())
                    .anyMatch(e -> e instanceof AdPlacementStatusChangedEvent)
                    .anyMatch(e -> e instanceof AdPlacementApprovedEvent);
        }

        @Test
        void rejectsBlankAdminId() {
            AdPlacement draft = newDraft();
            assertThatThrownBy(() -> draft.approve(" "))
                    .isInstanceOf(AdvertisingValidationException.class);
        }

        @Test
        void cannotApproveNonDraftPlacement() {
            AdPlacement pending = newDraft().approve("admin-1");
            assertThatThrownBy(() -> pending.approve("admin-2"))
                    .isInstanceOf(PlacementStateTransitionException.class);
        }
    }

    @Nested
    class Reject {

        @Test
        void movesFromDraftToCancelledWithReason() {
            AdPlacement rejected = newDraft().reject("admin-1", "bad content");

            assertThat(rejected.getStatus()).isEqualTo(PlacementStatus.CANCELLED);
            assertThat(rejected.getRejectionReason()).isEqualTo("bad content");
            assertThat(rejected.getRejectedByAdminId()).isEqualTo("admin-1");
            assertThat(rejected.getDomainEvents())
                    .anyMatch(e -> e instanceof AdPlacementRejectedEvent);
        }

        @Test
        void rejectsBlankReason() {
            AdPlacement draft = newDraft();
            assertThatThrownBy(() -> draft.reject("admin-1", "  "))
                    .isInstanceOf(AdvertisingValidationException.class);
        }

        @Test
        void cannotRejectNonDraftPlacement() {
            AdPlacement approved = newDraft().approve("admin-1");
            assertThatThrownBy(() -> approved.reject("admin-1", "too late"))
                    .isInstanceOf(PlacementStateTransitionException.class);
        }
    }

    @Nested
    class Counters {

        @Test
        void recordImpressionIncrementsCounter() {
            AdPlacement placement = newDraft();
            AdPlacement bumped = placement.recordImpression().recordImpression();
            assertThat(bumped.getImpressionsCount()).isEqualTo(2L);
        }

        @Test
        void recordClickIncrementsCounter() {
            AdPlacement placement = newDraft();
            AdPlacement bumped = placement.recordClick();
            assertThat(bumped.getClicksCount()).isEqualTo(1L);
        }
    }
}
