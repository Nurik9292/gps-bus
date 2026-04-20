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
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetCityByIdUseCaseTest {

    @InjectMocks
    private GetCityByIdUseCase useCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsCityWhenFound() {
        City city = City.create("Ashgabat", "Aşgabat", 0);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(city.getId())).thenReturn(Mono.just(city));

        StepVerifier.create(useCase.execute(Mono.just(city.getId().getValue())))
                .assertNext(result -> assertEquals("Ashgabat", result.name()))
                .verifyComplete();
    }

    @Test
    void errorsWhenCityNotFound() {
        String id = CityId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(any(CityId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id)))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(IllegalArgumentException.class, err);
                    assertEquals("City not found with ID: " + id, err.getMessage());
                })
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
