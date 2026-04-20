package biz.ugur.busroutebackend.client.application.usecase.city;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetMobileCitiesUseCaseTest {

    @InjectMocks
    private GetMobileCitiesUseCase useCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsActiveCitiesSortedByDisplayOrder() {
        City a = City.create("Balkanabat", "Balkanabat", true, 3);
        City b = City.create("Ashgabat", "Aşgabat", true, 1);
        City c = City.create("Mary", "Mary", true, 2);

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(cityRepository.findActiveCities()).thenReturn(Flux.just(a, b, c));

        StepVerifier.create(useCase.execute(null))
                .assertNext(list -> {
                    assertEquals(3, list.size());
                    assertEquals("Ashgabat", list.get(0).name());
                    assertEquals("Mary", list.get(1).name());
                    assertEquals("Balkanabat", list.get(2).name());
                })
                .verifyComplete();
    }

    @Test
    void exposesClientBoundContext() {
        assertEquals("client", useCase.getBoundContext());
    }
}
