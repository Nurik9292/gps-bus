package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AdPlacementResponseMapperTest {

    private final AdPlacementTargetRepository targetRepository =
            Mockito.mock(AdPlacementTargetRepository.class);

    private final PaymentRepository paymentRepository =
            Mockito.mock(PaymentRepository.class);

    private final AdPlacementResponseMapper mapper =
            new AdPlacementResponseMapper(targetRepository, paymentRepository);

    @Test
    void toResponseCopiesPlacementFieldsAndIncludesTargetsAlreadyOnAggregate() {
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "My Ad", "Body", "/img.jpg", "https://x.tm", "Click",
                ContentType.CONTENT, null,
                List.of(PlacementTarget.general(TargetType.HOME)), 1);

        when(paymentRepository.findFirstBySubjectAndProviderAndStatus(
                eq(PaymentSubjectType.AD_PLACEMENT),
                eq(placement.getId().getValue()),
                eq(PaymentProvider.CASH),
                eq(PaymentStatus.REGISTERED)))
                .thenReturn(Mono.empty());

        StepVerifier.create(mapper.toResponse(placement))
                .assertNext(response -> {
                    assertEquals(placement.getId().getValue(), response.id());
                    assertEquals("My Ad", response.title());
                    assertEquals("Body", response.content());
                    assertEquals("https://x.tm", response.targetUrl());
                    assertEquals("Click", response.ctaText());
                    assertNotNull(response.status());
                    assertEquals(1, response.targets().size());
                    assertEquals("HOME", response.targets().get(0).targetType());
                    assertNull(response.pendingCashPayment());
                })
                .verifyComplete();
    }

    @Test
    void toResponseFetchesTargetsFromRepoWhenAggregateHasNone() {
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "My Ad", null, null, "https://target", null,
                ContentType.LINK, null, null, 0);

        when(targetRepository.findByPlacementId(placement.getId()))
                .thenReturn(Flux.just(
                        PlacementTarget.specific(TargetType.ROUTE, "route-14"),
                        PlacementTarget.general(TargetType.HOME)));

        when(paymentRepository.findFirstBySubjectAndProviderAndStatus(
                eq(PaymentSubjectType.AD_PLACEMENT),
                eq(placement.getId().getValue()),
                eq(PaymentProvider.CASH),
                eq(PaymentStatus.REGISTERED)))
                .thenReturn(Mono.empty());

        StepVerifier.create(mapper.toResponse(placement))
                .assertNext(response -> {
                    assertEquals(2, response.targets().size());
                    assertNull(response.pendingCashPayment());
                })
                .verifyComplete();
    }

    @Test
    void toResponsesReturnsNullPendingCashPaymentForBatchWithoutLookup() {
        AdPlacement p1 = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "A", null, null, "https://target", null,
                ContentType.LINK, null, null, 0);
        AdPlacement p2 = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "B", null, null, "https://target", null,
                ContentType.LINK, null, null, 0);

        when(targetRepository.findByPlacementIds(any()))
                .thenReturn(Mono.just(Map.of(
                        p1.getId().getValue(), List.of(PlacementTarget.general(TargetType.HOME)),
                        p2.getId().getValue(), List.of(
                                PlacementTarget.specific(TargetType.STOP, "stop-1"),
                                PlacementTarget.general(TargetType.POPUP)))));

        StepVerifier.create(mapper.toResponses(List.of(p1, p2)).collectList())
                .assertNext(list -> {
                    assertEquals(2, list.size());
                    assertEquals(1, list.get(0).targets().size());
                    assertEquals(2, list.get(1).targets().size());
                    assertNull(list.get(0).pendingCashPayment());
                    assertNull(list.get(1).pendingCashPayment());
                })
                .verifyComplete();
    }

    @Test
    void toResponsesReturnsEmptyForEmptyInput() {
        StepVerifier.create(mapper.toResponses(List.of()).collectList())
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }
}
