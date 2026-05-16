package biz.ugur.busroutebackend.shared.utility;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BannerTypeTargetTypeMapperTest {

    @Test
    void main_to_home() {
        assertEquals(TargetType.HOME, BannerTypeTargetTypeMapper.toTarget(BannerType.MAIN));
    }

    @Test
    void stops_to_stops_list() {
        assertEquals(TargetType.STOPS_LIST, BannerTypeTargetTypeMapper.toTarget(BannerType.STOPS));
    }

    @Test
    void routes_to_routes_list() {
        assertEquals(TargetType.ROUTES_LIST, BannerTypeTargetTypeMapper.toTarget(BannerType.ROUTES));
    }

    @Test
    void places_to_places_list() {
        assertEquals(TargetType.PLACES_LIST, BannerTypeTargetTypeMapper.toTarget(BannerType.PLACES));
    }

    @Test
    void popup_to_popup() {
        assertEquals(TargetType.POPUP, BannerTypeTargetTypeMapper.toTarget(BannerType.POPUP));
    }

    @Test
    void stop_button_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> BannerTypeTargetTypeMapper.toTarget(BannerType.STOP_BUTTON));
        assertTrue(ex.getMessage().contains("STOP_BUTTON"));
    }

    @Test
    void from_target_reverse() {
        assertEquals(BannerType.MAIN, BannerTypeTargetTypeMapper.fromTarget(TargetType.HOME));
        assertEquals(BannerType.STOPS, BannerTypeTargetTypeMapper.fromTarget(TargetType.STOPS_LIST));
        assertEquals(BannerType.ROUTES, BannerTypeTargetTypeMapper.fromTarget(TargetType.ROUTES_LIST));
        assertEquals(BannerType.PLACES, BannerTypeTargetTypeMapper.fromTarget(TargetType.PLACES_LIST));
        assertEquals(BannerType.POPUP, BannerTypeTargetTypeMapper.fromTarget(TargetType.POPUP));
    }

    @Test
    void from_target_for_unmapped_returns_null() {
        assertNull(BannerTypeTargetTypeMapper.fromTarget(TargetType.ROUTE));
        assertNull(BannerTypeTargetTypeMapper.fromTarget(TargetType.STOP));
        assertNull(BannerTypeTargetTypeMapper.fromTarget(TargetType.PLACE));
    }
}
