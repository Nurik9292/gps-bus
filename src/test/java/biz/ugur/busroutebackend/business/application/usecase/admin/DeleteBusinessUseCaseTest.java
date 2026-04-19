package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessNotFoundException;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
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
class DeleteBusinessUseCaseTest {

    @InjectMocks
    private DeleteBusinessUseCase useCase;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void deletesExistingBusiness() {
        BusinessId id = BusinessId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.existsById(id)).thenReturn(Mono.just(true));
        when(businessRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id.getValue()))
                .verifyComplete();

        verify(businessRepository).deleteById(id);
    }

    @Test
    void errorsWhenBusinessNotFound() {
        BusinessId id = BusinessId.generate();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.existsById(id)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(id.getValue()))
                .expectErrorSatisfies(err -> assertInstanceOf(BusinessNotFoundException.class, err))
                .verify();

        verify(businessRepository, never()).deleteById(any(BusinessId.class));
    }

    @Test
    void exposesBoundContext() {
        assertEquals("business.admin", useCase.getBoundContext());
    }
}
