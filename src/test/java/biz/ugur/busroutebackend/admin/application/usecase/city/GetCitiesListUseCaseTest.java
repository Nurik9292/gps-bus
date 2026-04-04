package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetCitiesListUseCaseTest {

    @InjectMocks
    private GetCitiesListUseCase getCitiesListUseCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    @Test
    void shouldReturnActiveCitiesWhenActiveOnlyIsTrue() {
        City city1 = City.create("Ashgabat", "Aşgabat", 1);
        City city2 = City.create("Mary", "Mary", 2);
        City city3 = City.create("Turkmenabat", "Türkmenabat", 3);

        when(cityRepository.findActiveCities()).thenReturn(Flux.just(city1, city2, city3));

        Mono<List<CityResult>> result = getCitiesListUseCase.process(Mono.just(true));

        StepVerifier.create(result)
            .assertNext(cities -> {
                assertNotNull(cities);
                assertEquals(3, cities.size());

                assertEquals("Ashgabat", cities.get(0).name());
                assertEquals("Mary", cities.get(1).name());
                assertEquals("Turkmenabat", cities.get(2).name());

                cities.forEach(city -> {
                    assertNotNull(city.id());
                    assertNotNull(city.name());
                    assertNotNull(city.isActive());
                    assertNotNull(city.displayOrder());
                });
            })
            .verifyComplete();

        verify(cityRepository, times(1)).findActiveCities();
        verify(cityRepository, never()).findAll();
    }

    @Test
    void shouldReturnAllCitiesWhenActiveOnlyIsFalse() {
        City city1 = City.create("Ashgabat", "Aşgabat", 1);
        City city2 = City.create("Mary", "Mary", 2).deactivate();
        City city3 = City.create("Turkmenabat", "Türkmenabat", 3);

        when(cityRepository.findAll()).thenReturn(Flux.just(city1, city2, city3));

        Mono<List<CityResult>> result = getCitiesListUseCase.process(Mono.just(false));

        StepVerifier.create(result)
            .assertNext(cities -> {
                assertNotNull(cities);
                assertEquals(3, cities.size());

                assertTrue(cities.stream().anyMatch(c -> c.name().equals("Mary") && !c.isActive()));
                assertTrue(cities.stream().anyMatch(c -> c.name().equals("Ashgabat") && c.isActive()));
            })
            .verifyComplete();

        verify(cityRepository, times(1)).findAll();
        verify(cityRepository, never()).findActiveCities();
    }

    @Test
    void shouldReturnEmptyListWhenNoCitiesExist() {
        when(cityRepository.findActiveCities()).thenReturn(Flux.empty());

        Mono<List<CityResult>> result = getCitiesListUseCase.process(Mono.just(true));

        StepVerifier.create(result)
            .assertNext(cities -> {
                assertNotNull(cities);
                assertTrue(cities.isEmpty());
            })
            .verifyComplete();

        verify(cityRepository, times(1)).findActiveCities();
    }

    @Test
    void shouldSortCitiesByDisplayOrderThenName() {
        City city1 = City.create("Zebra City", "Zebra", 2);
        City city2 = City.create("Alpha City", "Alpha", 1);
        City city3 = City.create("Beta City", "Beta", 1);

        when(cityRepository.findActiveCities()).thenReturn(Flux.just(city1, city2, city3));

        Mono<List<CityResult>> result = getCitiesListUseCase.process(Mono.just(true));

        StepVerifier.create(result)
            .assertNext(cities -> {
                assertNotNull(cities);
                assertEquals(3, cities.size());

                assertEquals("Alpha City", cities.get(0).name());
                assertEquals(1, cities.get(0).displayOrder());

                assertEquals("Beta City", cities.get(1).name());
                assertEquals(1, cities.get(1).displayOrder());

                assertEquals("Zebra City", cities.get(2).name());
                assertEquals(2, cities.get(2).displayOrder());
            })
            .verifyComplete();
    }

    @Test
    void shouldHandleCitiesWithNullDisplayOrder() {
        City city1 = City.create("City With Order", "City", 1);
        City city2 = City.builder()
            .id(CityId.generate())
            .name("City Without Order")
            .nameTm("City")
            .isActive(true)
            .displayOrder(null)
            .build();

        when(cityRepository.findActiveCities()).thenReturn(Flux.just(city1, city2));

        Mono<List<CityResult>> result = getCitiesListUseCase.process(Mono.just(true));

        StepVerifier.create(result)
            .assertNext(cities -> {
                assertNotNull(cities);
                assertEquals(2, cities.size());

                assertEquals("City With Order", cities.get(0).name());
                assertEquals(1, cities.get(0).displayOrder());

                assertEquals("City Without Order", cities.get(1).name());
                assertNull(cities.get(1).displayOrder());
            })
            .verifyComplete();
    }

    @Test
    void shouldReturnCorrectBoundContext() {
        String boundContext = getCitiesListUseCase.getBoundContext();

        assertNotNull(boundContext);
        assertEquals("admin", boundContext);
    }

    @Test
    void shouldIncludeAllCityFieldsInResult() {
        City city = City.create("Ashgabat", "Aşgabat", 1);

        when(cityRepository.findActiveCities()).thenReturn(Flux.just(city));

        Mono<List<CityResult>> result = getCitiesListUseCase.process(Mono.just(true));

        StepVerifier.create(result)
            .assertNext(cities -> {
                assertEquals(1, cities.size());
                CityResult cityResult = cities.get(0);

                assertNotNull(cityResult.id(), "id should not be null");
                assertEquals("Ashgabat", cityResult.name(), "name should match");
                assertEquals("Aşgabat", cityResult.nameTm(), "nameTm should match");
                assertTrue(cityResult.isActive(), "isActive should be true");
                assertEquals(1, cityResult.displayOrder(), "displayOrder should match");
            })
            .verifyComplete();
    }
}
