package biz.ugur.busroutebackend.advertising.infrastructure.migration;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayContextsBackfillRunnerTest {

    @Test
    void parsesHomePopupRoutesStopsPlaces() {
        List<PlacementTarget> targets = DisplayContextsBackfillRunner.parseTargets(
                "placement-1", "home,popup,routes,stops,places");

        assertThat(targets).hasSize(5);
        assertThat(targets).extracting(PlacementTarget::getTargetType)
                .containsExactlyInAnyOrder(
                        TargetType.HOME, TargetType.POPUP,
                        TargetType.ROUTES_LIST, TargetType.STOPS_LIST, TargetType.PLACES_LIST);
        assertThat(targets).extracting(PlacementTarget::getTargetId)
                .containsOnlyNulls();
    }

    @Test
    void trimsWhitespaceAndIsCaseInsensitive() {
        List<PlacementTarget> targets = DisplayContextsBackfillRunner.parseTargets(
                "p", "  HOME  ,  Popup  , Stops");

        assertThat(targets).extracting(PlacementTarget::getTargetType)
                .containsExactlyInAnyOrder(TargetType.HOME, TargetType.POPUP, TargetType.STOPS_LIST);
    }

    @Test
    void skipsUnknownTokensWithoutFailing() {
        List<PlacementTarget> targets = DisplayContextsBackfillRunner.parseTargets(
                "p", "home,map.html,foo,routes");

        assertThat(targets).extracting(PlacementTarget::getTargetType)
                .containsExactlyInAnyOrder(TargetType.HOME, TargetType.ROUTES_LIST);
    }

    @Test
    void deduplicatesRepeatedTokens() {
        List<PlacementTarget> targets = DisplayContextsBackfillRunner.parseTargets(
                "p", "home,home,POPUP,popup");

        assertThat(targets).hasSize(2);
        assertThat(targets).extracting(PlacementTarget::getTargetType)
                .containsExactlyInAnyOrder(TargetType.HOME, TargetType.POPUP);
    }

    @Test
    void emptyOrNullInputYieldsEmptyList() {
        assertThat(DisplayContextsBackfillRunner.parseTargets("p", null)).isEmpty();
        assertThat(DisplayContextsBackfillRunner.parseTargets("p", "")).isEmpty();
        assertThat(DisplayContextsBackfillRunner.parseTargets("p", "   ")).isEmpty();
    }

    @Test
    void onlyUnknownTokensYieldsEmptyList() {
        assertThat(DisplayContextsBackfillRunner.parseTargets("p", "map.html,unknown,xyz")).isEmpty();
    }
}
