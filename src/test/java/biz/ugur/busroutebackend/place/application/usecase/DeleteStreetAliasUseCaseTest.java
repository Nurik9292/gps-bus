package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
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
class DeleteStreetAliasUseCaseTest {

    @InjectMocks
    private DeleteStreetAliasUseCase useCase;

    @Mock
    private StreetAliasRepository streetAliasRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesAliasById() {
        StreetAliasId id = StreetAliasId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(streetAliasRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(id.getValue())))
                .verifyComplete();

        verify(streetAliasRepository).deleteById(id);
    }

    @Test
    void exposesPlaceBoundContext() {
        assertEquals("place", useCase.getBoundContext());
    }
}
