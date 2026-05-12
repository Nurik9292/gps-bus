package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CreateCity;
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
class CreateCityUseCaseTest {

    @InjectMocks
    private CreateCityUseCase useCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsCityWhenNameIsUnique() {
        CreateCity create = new CreateCity("Balkanabat", "Balkanabat", true, 3, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.existsByName("Balkanabat")).thenReturn(Mono.just(false));
        when(cityRepository.save(any(City.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(create)))
                .assertNext(result -> {
                    assertEquals("Balkanabat", result.name());
                    assertEquals("Balkanabat", result.nameTm());
                    assertEquals(3, result.displayOrder());
                })
                .verifyComplete();

        verify(cityRepository).save(any(City.class));
    }

    @Test
    void errorsWhenCityAlreadyExists() {
        CreateCity create = new CreateCity("Ashgabat", "Aşgabat", true, 0, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.existsByName("Ashgabat")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(Mono.just(create)))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(IllegalArgumentException.class, err);
                    assertEquals("City already exists: Ashgabat", err.getMessage());
                })
                .verify();

        verify(cityRepository, never()).save(any());
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
