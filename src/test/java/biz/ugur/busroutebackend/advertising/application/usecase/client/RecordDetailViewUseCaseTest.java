package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.RecordDetailViewCommand;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdDetailViewEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
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
class RecordDetailViewUseCaseTest {

    @Mock private AdDetailViewEventRepository repository;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private RecordDetailViewUseCase useCase;
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new RecordDetailViewUseCase(repository, clock, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void process_savesDetailViewEvent_withDuration() {
        UUID placementId = UUID.randomUUID();
        when(repository.save(any(AdDetailViewEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new RecordDetailViewCommand(
                placementId.toString(), 8400, TargetType.PLACE, null
        ))).verifyComplete();

        ArgumentCaptor<AdDetailViewEvent> captor = ArgumentCaptor.forClass(AdDetailViewEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().durationMs()).isEqualTo(8400);
    }

    @Test
    void process_durationOutOfRange_propagatesIllegalArgumentException() {
        UUID placementId = UUID.randomUUID();

        StepVerifier.create(useCase.execute(new RecordDetailViewCommand(
                placementId.toString(), -1, null, null
        ))).expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
          .verify();
    }
}
