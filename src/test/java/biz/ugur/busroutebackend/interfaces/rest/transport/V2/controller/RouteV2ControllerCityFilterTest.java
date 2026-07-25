package biz.ugur.busroutebackend.interfaces.rest.transport.V2.controller;

import biz.ugur.busroutebackend.interfaces.rest.transport.V2.response.RouteSummaryV2;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetAllBusRoutesUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByIdUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteV2ControllerCityFilterTest {

    @Mock
    private MessageSource messageSource;
    @Mock
    private GetRouteByIdUseCase getRouteByIdUseCase;
    @Mock
    private GetAllBusRoutesUseCase getAllBusRoutesUseCase;

    private RouteV2Controller controller;

    @BeforeEach
    void setUp() {
        controller = new RouteV2Controller(messageSource, getRouteByIdUseCase, getAllBusRoutesUseCase);
        RouteList routeList = new RouteList(List.of(
                routeIn("route-legacy-1", "city-001"),
                routeIn("route-legacy-133", "city-006"),
                routeIn("balkan-1", "city-004")), 3L, 1, 50, 3);
        when(getAllBusRoutesUseCase.execute(any())).thenReturn(Mono.just(routeList));
    }

    private RouteData routeIn(String id, String cityId) {
        return new RouteData(id, "1", "Маршрут 1", "", "", "#FF0000", cityId, true,
                30, 0, 0, null, null, List.of(), List.of(), 0L,
                null, null, null, List.of(), List.of());
    }

    @Test
    void cityIdFiltersNamesakesToSingleCity() {
        StepVerifier.create(controller.getAllRoutes("city-004"))
                .assertNext(entity -> {
                    List<RouteSummaryV2> routes = entity.getBody().getData();
                    assertThat(routes).hasSize(1);
                    assertThat(routes.get(0).cityId()).isEqualTo("city-004");
                })
                .verifyComplete();
    }

    @Test
    void missingCityIdKeepsFullListAsBefore() {
        StepVerifier.create(controller.getAllRoutes(null))
                .assertNext(entity ->
                        assertThat(entity.getBody().getData()).hasSize(3))
                .verifyComplete();
    }

    @Test
    void blankCityIdKeepsFullList() {
        StepVerifier.create(controller.getAllRoutes(" "))
                .assertNext(entity ->
                        assertThat(entity.getBody().getData()).hasSize(3))
                .verifyComplete();
    }
}
