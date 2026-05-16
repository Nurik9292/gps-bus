package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.RecordClickCommand;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdClickEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordClickUseCaseTest {

    @Mock private AdClickEventRepository repository;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private RecordClickUseCase useCase;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new RecordClickUseCase(repository, clock, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void process_propagatesRepositoryError() {
        UUID placementId = UUID.randomUUID();
        RuntimeException dbFailure = new RuntimeException("db failure");
        when(repository.save(any(AdClickEvent.class))).thenReturn(Mono.error(dbFailure));

        StepVerifier.create(useCase.execute(new RecordClickCommand(
                placementId.toString(), null, null
        ))).expectErrorSatisfies(err ->
                org.assertj.core.api.Assertions.assertThat(err).isSameAs(dbFailure))
          .verify();
    }

    @Test
    void process_savesEventThroughRepository() {
        UUID placementId = UUID.randomUUID();
        when(repository.save(any(AdClickEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new RecordClickCommand(
                placementId.toString(), TargetType.POPUP, null
        ))).verifyComplete();

        ArgumentCaptor<AdClickEvent> captor = ArgumentCaptor.forClass(AdClickEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(TargetType.POPUP);
    }
}
