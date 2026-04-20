package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.CreatePlaceAliasInput;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
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
class CreatePlaceAliasUseCaseTest {

    @InjectMocks
    private CreatePlaceAliasUseCase useCase;

    @Mock
    private PlaceAliasRepository placeAliasRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsAliasAndReturnsResult() {
        CreatePlaceAliasInput input = new CreatePlaceAliasInput("place-1", "MyAlias", "ru");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placeAliasRepository.save(any(PlaceAlias.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(input)))
                .assertNext(result -> {
                    assertEquals("MyAlias", result.alias());
                    assertEquals("ru", result.language());
                })
                .verifyComplete();

        verify(placeAliasRepository).save(any(PlaceAlias.class));
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
