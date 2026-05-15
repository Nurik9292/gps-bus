package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CancelAdPlacementUseCaseTest {

    @InjectMocks
    private CancelAdPlacementUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AdPlacementResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private AdPlacement placement;

    @BeforeEach
    void setUp() {
        placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", null, null, "https://target", null,
                ContentType.LINK, null, null, 0);
    }

    @Test
    void cancelsAndReturnsResponse() {
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.id()).thenReturn(placement.getId().getValue());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findById(placement.getId())).thenReturn(Mono.just(placement));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.findFirstBySubjectAndProviderAndStatus(any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        when(responseMapper.toResponse(any(AdPlacement.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(placement.getId().getValue()))
                .expectNextCount(1)
                .verifyComplete();

        verify(placementRepository).save(any(AdPlacement.class));
    }

    @Test
    void cancelsPendingCashPaymentAlongsidePlacement() {
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.id()).thenReturn(placement.getId().getValue());

        Payment pendingCash = mock(Payment.class);
        Payment cancelledPayment = mock(Payment.class);
        when(pendingCash.cancel(anyString())).thenReturn(cancelledPayment);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findById(placement.getId())).thenReturn(Mono.just(placement));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.findFirstBySubjectAndProviderAndStatus(
                eq(PaymentSubjectType.AD_PLACEMENT),
                eq(placement.getId().getValue()),
                eq(PaymentProvider.CASH),
                eq(PaymentStatus.REGISTERED)))
                .thenReturn(Mono.just(pendingCash));
        when(paymentRepository.save(cancelledPayment)).thenReturn(Mono.just(cancelledPayment));
        when(responseMapper.toResponse(any(AdPlacement.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(placement.getId().getValue()))
                .expectNextCount(1)
                .verifyComplete();

        verify(paymentRepository).save(cancelledPayment);
    }

    @Test
    void noPendingCashPayment_completesNormally() {
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.id()).thenReturn(placement.getId().getValue());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findById(placement.getId())).thenReturn(Mono.just(placement));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentRepository.findFirstBySubjectAndProviderAndStatus(any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        when(responseMapper.toResponse(any(AdPlacement.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(placement.getId().getValue()))
                .expectNextCount(1)
                .verifyComplete();

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void errorsWhenPlacementNotFound() {
        String id = PlacementId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findById(any(PlacementId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .expectErrorSatisfies(err -> assertInstanceOf(AdPlacementNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
