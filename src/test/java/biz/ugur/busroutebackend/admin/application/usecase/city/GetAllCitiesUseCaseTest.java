package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.GetAllCitiesInput;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllCitiesUseCaseTest {

    @InjectMocks
    private GetAllCitiesUseCase useCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAllCitiesWhenNoFilter() {
        City c1 = City.create("Ashgabat", "Aşgabat", 1);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(c1));
        when(cityRepository.count()).thenReturn(Mono.just(1L));
        when(cityRepository.countActiveCities()).thenReturn(Mono.just(1L));

        GetAllCitiesInput input = GetAllCitiesInput.fromParams(1, 10, null, null, null, null);

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> {
                    assertEquals(1, list.getCities().size());
                    assertEquals(1L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void appliesSearchSpecification() {
        City c = City.create("Mary", "Mary", 2);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(c));
        when(cityRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(cityRepository.countActiveCities()).thenReturn(Mono.just(3L));

        GetAllCitiesInput input = GetAllCitiesInput.fromParams(1, 10, null, null, true, "Mary");

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(list -> assertEquals(1, list.getCities().size()))
                .verifyComplete();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
