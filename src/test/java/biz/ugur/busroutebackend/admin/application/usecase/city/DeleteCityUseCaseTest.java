package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeleteCityUseCaseTest {

    @InjectMocks
    private DeleteCityUseCase useCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesExistingCity() {
        City city = City.create("Mary", "Mary", 1);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(city.getId())).thenReturn(Mono.just(city));
        when(cityRepository.deleteById(city.getId())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(city.getId().getValue())))
                .verifyComplete();

        verify(cityRepository).deleteById(city.getId());
    }

    @Test
    void errorsWhenCityNotFound() {
        String id = CityId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(any(CityId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();

        verify(cityRepository, never()).deleteById(any(CityId.class));
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
