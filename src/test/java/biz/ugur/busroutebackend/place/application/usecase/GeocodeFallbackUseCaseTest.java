package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.GeocodingResult;
import biz.ugur.busroutebackend.place.domain.services.GeocodingService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodeFallbackUseCaseTest {

    @InjectMocks
    private GeocodeFallbackUseCase useCase;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private void stubCorrelation() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void mapsGeocodingResultsToPlaceSearchResultsWithNullIdAndFixedScore() {
        stubCorrelation();
        GeocodingResult geo = new GeocodingResult("Berkarar, Ak bugdaý etraby",
                "Ahal welaýaty", null, null, "Ahal",
                new BigDecimal("37.90"), new BigDecimal("58.30"), "nominatim");
        when(geocodingService.search("Berkar", 5)).thenReturn(Mono.just(List.of(geo)));

        StepVerifier.create(useCase.execute(Mono.just(new GeocodeFallbackUseCase.Query("Berkar", 5))))
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    var place = results.get(0);
                    assertThat(place.id()).isNull();
                    assertThat(place.name()).isEqualTo("Berkarar, Ak bugdaý etraby");
                    assertThat(place.address()).isEqualTo("Ahal welaýaty");
                    assertThat(place.similarityScore()).isEqualTo(GeocodeFallbackUseCase.GEOCODED_SCORE);
                    assertThat(place.aliases()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void geocodingErrorPropagates() {
        stubCorrelation();
        when(geocodingService.search("Berkar", 5))
                .thenReturn(Mono.error(new IllegalStateException("nominatim down")));

        StepVerifier.create(useCase.execute(Mono.just(new GeocodeFallbackUseCase.Query("Berkar", 5))))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalStateException.class, err))
                .verify();
    }
}
