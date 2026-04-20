package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.CreateStreetCommand;
import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateStreetUseCaseTest {

    @InjectMocks
    private CreateStreetUseCase useCase;

    @Mock
    private StreetRepository streetRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsStreetAndReturnsResult() {
        CreateStreetCommand cmd = new CreateStreetCommand(
                "Lenin", "Lenin St.", "Lenin köç.", "ashgabat");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetRepository.save(any(Street.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(result -> {
                    assertEquals("Lenin", result.name());
                    assertEquals("Lenin St.", result.nameEn());
                    assertEquals("ashgabat", result.cityId());
                })
                .verifyComplete();

        verify(streetRepository).save(any(Street.class));
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
