package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalPlacementProtectionTest {

    private static AdPlacement external() {
        return AdPlacement.createExternal("svc-1", "ref-1", PlacementType.BANNER,
                "Внешний", null, "/img.png", "https://t", null, ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(3)),
                List.of(PlacementTarget.general(TargetType.ROUTES_LIST)), 1)
                .toBuilder().status(PlacementStatus.ACTIVE).build();
    }

    private static AdPlacement manual() {
        return AdPlacement.create(null, null, PlacementType.BANNER, PlacementKind.EDITORIAL,
                "Ручной", null, "/img.png", "https://t", null, ContentType.LINK,
                PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(3)),
                List.of(PlacementTarget.general(TargetType.ROUTES_LIST)), 1)
                .toBuilder().status(PlacementStatus.ACTIVE).build();
    }

    @Test
    void manualEditIsRejectedForExternalPlacement() {
        assertThatThrownBy(() -> external().ensureEditableByAdmin())
                .isInstanceOf(AdvertisingValidationException.class)
                .hasMessageContaining("external");
    }

    @Test
    void manualPlacementStaysEditable() {
        assertThatCode(() -> manual().ensureEditableByAdmin()).doesNotThrowAnyException();
    }

    @Test
    void adminMayStillPauseExternalPlacement() {
        AdPlacement paused = external().markAsPaused();

        assertThat(paused.getStatus()).isEqualTo(PlacementStatus.PAUSED);
        assertThat(paused.isExternal()).isTrue();
        assertThat(paused.getExternalRef()).isEqualTo("ref-1");
    }

    @Test
    void ownerIdentitySurvivesStatusChange() {
        AdPlacement paused = external().markAsPaused();

        assertThat(paused.isOwnedBy("svc-1")).isTrue();
    }
}
