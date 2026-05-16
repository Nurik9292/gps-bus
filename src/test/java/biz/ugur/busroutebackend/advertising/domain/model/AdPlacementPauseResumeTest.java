package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.PlacementStateTransitionException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdPlacementPauseResumeTest {

    private AdPlacement activePlacement() {
        return AdPlacement.create(
                BusinessId.generate(),
                TariffId.generate(),
                PlacementType.BANNER,
                null,
                "Promo",
                null,
                "https://img",
                "https://target",
                "Click",
                ContentType.LINK,
                null,
                null,
                1
        ).approve("admin").markAsScheduled().markAsActive();
    }

    @Test
    void pauseFromActiveTransitionsToPaused() {
        AdPlacement paused = activePlacement().markAsPaused();
        assertThat(paused.getStatus()).isEqualTo(PlacementStatus.PAUSED);
    }

    @Test
    void resumeFromPausedTransitionsToActive() {
        AdPlacement resumed = activePlacement().markAsPaused().markAsResumed();
        assertThat(resumed.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
    }

    @Test
    void pauseFromDraftThrows() {
        AdPlacement draft = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(),
                PlacementType.BANNER, null,
                "Promo", null, "https://img", "https://target", "Click",
                ContentType.LINK, null, null, 1);
        assertThatThrownBy(draft::markAsPaused)
                .isInstanceOf(PlacementStateTransitionException.class);
    }

    @Test
    void pauseFromCancelledThrows() {
        AdPlacement cancelled = activePlacement().cancel();
        assertThatThrownBy(cancelled::markAsPaused)
                .isInstanceOf(PlacementStateTransitionException.class);
    }

    @Test
    void cancelFromPausedIsAllowed() {
        AdPlacement cancelled = activePlacement().markAsPaused().cancel();
        assertThat(cancelled.getStatus()).isEqualTo(PlacementStatus.CANCELLED);
    }
}
