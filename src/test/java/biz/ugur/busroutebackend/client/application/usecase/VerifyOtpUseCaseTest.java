package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class VerifyOtpUseCaseTest {

    @InjectMocks
    private VerifyOtpUseCase useCase;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void verifiesMatchingOtpAndReturnsVerifiedClient() {
        Client client = Client.create("John", "+99365000001", Platform.ANDROID).generateOtp();
        String otpCode = client.getOtpCode();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.findByPhone("+99365000001")).thenReturn(Mono.just(client));
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(
                new VerifyOtpUseCase.Command("+99365000001", otpCode))))
                .assertNext(result -> assertTrue(result.verified()))
                .verifyComplete();
    }

    @Test
    void errorsOnInvalidOtp() {
        Client client = Client.create("John", "+99365000001", Platform.ANDROID).generateOtp();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.findByPhone("+99365000001")).thenReturn(Mono.just(client));

        StepVerifier.create(useCase.execute(Mono.just(
                new VerifyOtpUseCase.Command("+99365000001", "000000"))))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void errorsWhenClientNotFoundByPhone() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.findByPhone(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new VerifyOtpUseCase.Command("+99365000001", "000000"))))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesClientBoundContext() {
        assertEquals("client", useCase.getBoundContext());
    }
}
