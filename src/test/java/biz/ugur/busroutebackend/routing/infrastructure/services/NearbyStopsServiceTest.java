package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.Location;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NearbyStopsServiceTest {

    @InjectMocks
    private NearbyStopsService nearbyStopsService;

    @Mock
    private RouteCalculationService routeCalculationService;

    private SearchContext testContext;
    private Location fromLocation;
    private Location toLocation;

    @BeforeEach
    void setUp() {
        fromLocation = new Location(38.32323, 58.34343, "Test From Location");
        toLocation = new Location(38.434343, 58.33433, "Test To Location");

        testContext = new SearchContext(
                "test-search-123",
                fromLocation,
                toLocation,
                TripSearchCriteria.defaultCriteria(),
                Instant.now().getEpochSecond()
        );
    }

    @Test
    @DisplayName("Should find stops for both locations with enhanced radius")
    void shouldFindStopsForBothLocationsWithEnhancedRadius() {
        List<BusStop> fromStops = createTestBusStops("from", 5);
        List<BusStop> toStops = createTestBusStops("to", 3);

        when(routeCalculationService.findNearbyStops(eq(fromLocation), eq(1.0)))
                .thenReturn(Flux.fromIterable(fromStops));
        when(routeCalculationService.findNearbyStops(eq(toLocation), eq(1.0)))
                .thenReturn(Flux.fromIterable(toStops));

        StepVerifier.create(nearbyStopsService.findStopsForBothLocations(testContext))
                .assertNext(stopsContext -> {
                    assertThat(stopsContext.fromStops()).hasSize(5);
                    assertThat(stopsContext.toStops()).hasSize(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find multiple layers of nearby stops")
    void shouldFindMultipleLayersOfNearbyStops() {
        // Layer 0 (0.3км): 2 остановки от, 1 до
        List<BusStop> layer0From = createTestBusStops("from_layer0", 2);
        List<BusStop> layer0To = createTestBusStops("to_layer0", 1);

        // Layer 1 (0.6км): 4 остановки от, 3 до
        List<BusStop> layer1From = createTestBusStops("from_layer1", 4);
        List<BusStop> layer1To = createTestBusStops("to_layer1", 3);

        // Layer 2 (1.0км): 6 остановок от, 5 до
        List<BusStop> layer2From = createTestBusStops("from_layer2", 6);
        List<BusStop> layer2To = createTestBusStops("to_layer2", 5);

        when(routeCalculationService.findNearbyStops(eq(fromLocation), eq(0.3)))
                .thenReturn(Flux.fromIterable(layer0From));
        when(routeCalculationService.findNearbyStops(eq(toLocation), eq(0.3)))
                .thenReturn(Flux.fromIterable(layer0To));

        when(routeCalculationService.findNearbyStops(eq(fromLocation), eq(0.6)))
                .thenReturn(Flux.fromIterable(layer1From));
        when(routeCalculationService.findNearbyStops(eq(toLocation), eq(0.6)))
                .thenReturn(Flux.fromIterable(layer1To));

        when(routeCalculationService.findNearbyStops(eq(fromLocation), eq(1.0)))
                .thenReturn(Flux.fromIterable(layer2From));
        when(routeCalculationService.findNearbyStops(eq(toLocation), eq(1.0)))
                .thenReturn(Flux.fromIterable(layer2To));

        // When: многослойный поиск
        StepVerifier.create(nearbyStopsService.findMultipleNearbyStops(testContext))
                .assertNext(multiStops -> {
                    // Then: найдены 3 слоя остановок
                    assertThat(multiStops.getLayerCount()).isEqualTo(3);

                    // Layer 0: ограничено 4 остановками от, всего 1 до
                    StopsContext layer0 = multiStops.getLayer(0);
                    assertThat(layer0.fromStops()).hasSize(2);
                    assertThat(layer0.toStops()).hasSize(1);

                    // Layer 1: ограничено 6 остановками от, всего 3 до
                    StopsContext layer1 = multiStops.getLayer(1);
                    assertThat(layer1.fromStops()).hasSize(4);
                    assertThat(layer1.toStops()).hasSize(3);

                    // Layer 2: ограничено 8 остановками от, всего 5 до
                    StopsContext layer2 = multiStops.getLayer(2);
                    assertThat(layer2.fromStops()).hasSize(6);
                    assertThat(layer2.toStops()).hasSize(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should prioritize major stops and high connectivity stops")
    void shouldPrioritizeMajorStopsAndHighConnectivityStops() {
        // Given: остановки с разными приоритетами
        List<BusStop> mixedStops = List.of(
                createBusStop("regular-stop", false, null),  // Обычная: getServingRoutesCount() = 2
                createBusStop("major-stop", true, null),     // Major: getServingRoutesCount() = 5
                createBusStop("another-regular", false, null), // Обычная: getServingRoutesCount() = 2
                createBusStop("major-connected", true, null)   // Major: getServingRoutesCount() = 5
        );

        when(routeCalculationService.findNearbyStops(any(Location.class), anyDouble()))
                .thenReturn(Flux.fromIterable(mixedStops));

        // When: поиск с приоритизацией
        StepVerifier.create(nearbyStopsService.findStopsForBothLocations(testContext))
                .assertNext(stopsContext -> {
                    // Then: остановки отсортированы по приоритету
                    List<BusStop> fromStops = stopsContext.fromStops();
                    assertThat(fromStops).isNotEmpty();

                    // Первыми должны быть major stops (они имеют getServingRoutesCount() = 5)
                    // Проверяем что major stops идут перед обычными
                    BusStop firstStop = fromStops.getFirst();
                    assertThat(firstStop.getIsMajorStop()).isTrue();
                    assertThat(firstStop.getServingRoutesCount()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty layer gracefully")
    void shouldHandleEmptyLayerGracefully() {
        // Given: пустой слой остановок
        NearbyStopsService.MultiLayerStopsContext emptyContext =
                new NearbyStopsService.MultiLayerStopsContext(List.of(), List.of());

        // When & Then: должен корректно обработать пустой слой
        assertThat(emptyContext.getLayerCount()).isEqualTo(0);
        assertThat(emptyContext.hasInsufficientStops()).isTrue();
        assertThat(emptyContext.getLayer(0)).isEqualTo(StopsContext.empty());
    }

    @Test
    @DisplayName("Should provide debug information")
    void shouldProvideDebugInformation() {
        // Given: контекст с несколькими слоями
        List<List<BusStop>> fromLayers = List.of(
                createTestBusStops("layer0", 2),
                createTestBusStops("layer1", 4)
        );
        List<List<BusStop>> toLayers = List.of(
                createTestBusStops("layer0", 1),
                createTestBusStops("layer1", 3)
        );

        NearbyStopsService.MultiLayerStopsContext context =
                new NearbyStopsService.MultiLayerStopsContext(fromLayers, toLayers);

        // When: получение debug информации
        String debugInfo = context.getDebugInfo();

        // Then: информация содержит детали по слоям
        assertThat(debugInfo).contains("layers=2");
        assertThat(debugInfo).contains("layer0=[from=2, to=1]");
        assertThat(debugInfo).contains("layer1=[from=4, to=3]");
    }


    private List<BusStop> createTestBusStops(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> createBusStop(prefix + "-stop-" + i, i % 2 == 0, null))
                .toList();
    }

    private BusStop createBusStop(String name, boolean isMajor, Integer routeCount) {
        BusStop stop = new BusStop(
                name,
                BusStopId.generate().getValue(),
                BigDecimal.valueOf(38.3 + Math.random() * 0.1),
                BigDecimal.valueOf(58.3 + Math.random() * 0.1)
        );

        // Устанавливаем isMajorStop через конструктор или сеттер если есть
        // Для тестов создадим остановку с нужными параметрами
        return new BusStop(
                BusStopId.generate(),
                name,
                null, // nameEn
                null, // nameTm
                null, // stopCode
                BigDecimal.valueOf(38.3 + Math.random() * 0.1),
                BigDecimal.valueOf(58.3 + Math.random() * 0.1),
                true, // isActive
                isMajor, // isMajorStop
                "test-city-id"
        );
    }
}