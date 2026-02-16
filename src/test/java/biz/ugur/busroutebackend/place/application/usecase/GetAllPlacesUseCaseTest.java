package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.GetAllPlacesInput;
import biz.ugur.busroutebackend.place.application.dto.PlaceList;
import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAllPlacesUseCase Tests")
class GetAllPlacesUseCaseTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Mock
    private EventBus eventBus;

    private GetAllPlacesUseCase useCase;
    private Place testPlace;

    @BeforeEach
    void setUp() {
        useCase = new GetAllPlacesUseCase(placeRepository, correlationContextService, eventBus);

        testPlace = Place.create("Тестовое место", "Test Place", null,
                "Описание", "Адрес", PlaceCategory.MALL, "city-1",
                new BigDecimal("37.95"), new BigDecimal("58.38"), null);

        when(correlationContextService.executeWithCorrelation(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Получение списка мест с пагинацией")
    void getAllPlacesWithPagination() {
        GetAllPlacesInput input = GetAllPlacesInput.fromParams(1, 20, "name", "asc", null, null, null);

        when(placeRepository.findByFilters(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Flux.just(testPlace));
        when(placeRepository.countByFilters(any(), any(), any()))
                .thenReturn(Mono.just(1L));
        when(placeRepository.count())
                .thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertInstanceOf(PlaceList.class, result);
                    assertEquals(1, result.getPlaces().size());
                    assertEquals("Тестовое место", result.getPlaces().getFirst().name());
                })
                .verifyComplete();

        verify(placeRepository).findByFilters(any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("Получение пустого списка")
    void getAllPlacesReturnsEmpty() {
        GetAllPlacesInput input = GetAllPlacesInput.fromParams(1, 20, "name", "asc", null, null, null);

        when(placeRepository.findByFilters(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Flux.empty());
        when(placeRepository.countByFilters(any(), any(), any()))
                .thenReturn(Mono.just(0L));
        when(placeRepository.count())
                .thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(0, result.getPlaces().size());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Получение мест с фильтром по городу")
    void getAllPlacesFilteredByCity() {
        GetAllPlacesInput input = GetAllPlacesInput.fromParams(1, 20, "name", "asc", "city-1", null, null);

        when(placeRepository.findByFilters(eq("city-1"), any(), any(), any(Pageable.class)))
                .thenReturn(Flux.just(testPlace));
        when(placeRepository.countByFilters(eq("city-1"), any(), any()))
                .thenReturn(Mono.just(1L));
        when(placeRepository.count())
                .thenReturn(Mono.just(5L));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(1, result.getPlaces().size());
                })
                .verifyComplete();

        verify(placeRepository).findByFilters(eq("city-1"), any(), any(), any(Pageable.class));
    }
}
