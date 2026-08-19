package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementSource;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdPlacementSourceTest {

    private static final String SERVICE_ID = "svc-1";
    private static final String EXTERNAL_REF = "banner-42";

    private static PlacementWindow window() {
        return PlacementWindow.of(LocalDateTime.now(), LocalDateTime.now().plusDays(7));
    }

    private static List<PlacementTarget> routesTarget() {
        return List.of(PlacementTarget.of(TargetType.ROUTES_LIST, null));
    }

    private static AdPlacement external(String serviceId, String externalRef) {
        return AdPlacement.createExternal(serviceId, externalRef, PlacementType.BANNER,
                "Внешний баннер", null, "https://cdn/img.png", "https://target",
                null, ContentType.LINK, window(), routesTarget(), 1);
    }

    @Test
    void manualPlacementIsDefaultSource() {
        AdPlacement placement = AdPlacement.create(null, null, PlacementType.BANNER,
                PlacementKind.EDITORIAL, "Ручной баннер", null, "https://cdn/img.png",
                "https://target", null, ContentType.LINK, window(), routesTarget(), 0);

        assertThat(placement.getSource()).isEqualTo(PlacementSource.MANUAL);
        assertThat(placement.getExternalServiceId()).isNull();
        assertThat(placement.getExternalRef()).isNull();
        assertThat(placement.isExternal()).isFalse();
    }

    @Test
    void externalPlacementCarriesOwnerAndReference() {
        AdPlacement placement = external(SERVICE_ID, EXTERNAL_REF);

        assertThat(placement.getSource()).isEqualTo(PlacementSource.EXTERNAL);
        assertThat(placement.getExternalServiceId()).isEqualTo(SERVICE_ID);
        assertThat(placement.getExternalRef()).isEqualTo(EXTERNAL_REF);
        assertThat(placement.isExternal()).isTrue();
        assertThat(placement.getKind()).isEqualTo(PlacementKind.EDITORIAL);
    }

    @Test
    void externalPlacementRequiresOwner() {
        assertThatThrownBy(() -> external(null, EXTERNAL_REF))
                .isInstanceOf(AdvertisingValidationException.class)
                .hasMessageContaining("externalServiceId");
    }

    @Test
    void externalPlacementRequiresReference() {
        assertThatThrownBy(() -> external(SERVICE_ID, "  "))
                .isInstanceOf(AdvertisingValidationException.class)
                .hasMessageContaining("externalRef");
    }

    @Test
    void externalPlacementRejectsNonRoutesTarget() {
        assertThatThrownBy(() -> AdPlacement.createExternal(SERVICE_ID, EXTERNAL_REF,
                PlacementType.BANNER, "Внешний баннер", null, "https://cdn/img.png",
                "https://target", null, ContentType.LINK, window(),
                List.of(PlacementTarget.of(TargetType.HOME, null)), 1))
                .isInstanceOf(AdvertisingValidationException.class)
                .hasMessageContaining("ROUTES_LIST");
    }

    @Test
    void ownershipIsCheckedByServiceId() {
        AdPlacement placement = external(SERVICE_ID, EXTERNAL_REF);

        assertThat(placement.isOwnedBy(SERVICE_ID)).isTrue();
        assertThat(placement.isOwnedBy("svc-other")).isFalse();
    }

    @Test
    void manualPlacementIsOwnedByNobody() {
        AdPlacement placement = AdPlacement.create(null, null, PlacementType.BANNER,
                PlacementKind.EDITORIAL, "Ручной", null, "https://cdn/img.png",
                "https://target", null, ContentType.LINK, window(), routesTarget(), 0);

        assertThat(placement.isOwnedBy(SERVICE_ID)).isFalse();
    }
}
