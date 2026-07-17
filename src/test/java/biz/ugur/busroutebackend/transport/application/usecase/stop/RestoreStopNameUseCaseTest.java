package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.application.dto.stop.UpdateStop;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.NameChangeRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestoreStopNameUseCaseTest {

    @InjectMocks
    private RestoreStopNameUseCase useCase;

    @Mock
    private BusStopRepository busStopRepository;
    @Mock
    private NameHistoryRepository nameHistoryRepository;
    @Mock
    private UpdateBusStopUseCase updateBusStopUseCase;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private void stubCorrelation() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void restoresPreviousStopNameThroughRegularUpdatePath() {
        stubCorrelation();
        BusStop stop = BusStop.builder()
                .id(BusStopId.of("stop-1"))
                .stopName("Новое имя")
                .nameEn("New name")
                .nameTm("Taze at")
                .latitude(new BigDecimal("37.95"))
                .longitude(new BigDecimal("58.38"))
                .isActive(true)
                .isMajorStop(false)
                .build();
        when(nameHistoryRepository.findByEntityAndField("STOP", "stop-1", "stopName"))
                .thenReturn(Mono.just(new NameChangeRecord("STOP", "stop-1", "stopName",
                        "Старое имя", "Новое имя", "admin", Instant.now())));
        when(busStopRepository.findById(BusStopId.of("stop-1"))).thenReturn(Mono.just(stop));
        StopData resultData = org.mockito.Mockito.mock(StopData.class);
        when(updateBusStopUseCase.execute(any(Mono.class))).thenReturn(Mono.just(resultData));

        StepVerifier.create(useCase.execute(Mono.just(
                        new RestoreStopNameUseCase.Query("stop-1", "stopName"))))
                .expectNext(resultData)
                .verifyComplete();

        ArgumentCaptor<Mono<UpdateStop>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(updateBusStopUseCase).execute(captor.capture());
        UpdateStop sent = captor.getValue().block();
        assertThat(sent.stopName()).isEqualTo("Старое имя");
        assertThat(sent.nameEn()).isEqualTo("New name");
        assertThat(sent.nameTm()).isEqualTo("Taze at");
    }

    @Test
    void failsWhenNoPreviousValueRecorded() {
        stubCorrelation();
        when(nameHistoryRepository.findByEntityAndField("STOP", "stop-1", "nameEn"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                        new RestoreStopNameUseCase.Query("stop-1", "nameEn"))))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(IllegalArgumentException.class, err);
                    assertThat(err.getMessage()).contains("No previous value");
                })
                .verify();

        verify(updateBusStopUseCase, never()).execute(any(Mono.class));
    }

    @Test
    void rejectsNonRestorableField() {
        stubCorrelation();
        StepVerifier.create(useCase.execute(Mono.just(
                        new RestoreStopNameUseCase.Query("stop-1", "latitude"))))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }
}
