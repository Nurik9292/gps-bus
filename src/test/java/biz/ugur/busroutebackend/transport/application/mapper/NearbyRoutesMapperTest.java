package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.NearbyRoutesResponse;
import biz.ugur.busroutebackend.transport.domain.valueobject.NearbyRouteInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NearbyRoutesMapperTest {

    private final NearbyRoutesMapper mapper = new NearbyRoutesMapper();

    @Test
    void toResponseWrapsItemsAndCount() {
        NearbyRoutesResponse response = mapper.toResponse(List.of(
                new NearbyRouteInfo("r-1", "1", "#FF0000"),
                new NearbyRouteInfo("r-2", "2", "#00FF00")
        ));

        assertEquals(2, response.getTotalCount());
        assertEquals(2, response.getRoutes().size());
        assertEquals("r-1", response.getRoutes().get(0).getId());
        assertEquals("2", response.getRoutes().get(1).getRouteNumber());
    }

    @Test
    void toResponseHandlesEmptyList() {
        NearbyRoutesResponse response = mapper.toResponse(List.of());

        assertEquals(0, response.getTotalCount());
        assertEquals(0, response.getRoutes().size());
    }

    @Test
    void toRouteItemCopiesAllFields() {
        NearbyRoutesResponse.NearbyRouteItem item = mapper.toRouteItem(
                new NearbyRouteInfo("r-7", "29A", "#FF5722"));

        assertEquals("r-7", item.getId());
        assertEquals("29A", item.getRouteNumber());
        assertEquals("#FF5722", item.getRouteColor());
    }
}
