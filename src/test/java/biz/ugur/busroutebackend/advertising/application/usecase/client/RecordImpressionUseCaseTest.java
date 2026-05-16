package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.RecordImpressionCommand;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdImpressionEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
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
class RecordImpressionUseCaseTest {

    @Mock private AdImpressionEventRepository repository;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private RecordImpressionUseCase useCase;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        useCase = new RecordImpressionUseCase(repository, clock, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void process_savesEventThroughRepository_withTargetType() {
        UUID placementId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(repository.save(any(AdImpressionEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new RecordImpressionCommand(
                placementId.toString(), TargetType.ROUTE, targetId
        ))).verifyComplete();

        ArgumentCaptor<AdImpressionEvent> captor = ArgumentCaptor.forClass(AdImpressionEvent.class);
        verify(repository).save(captor.capture());
        AdImpressionEvent saved = captor.getValue();
        assertThat(saved.placementId().getValue()).isEqualTo(placementId.toString());
        assertThat(saved.occurredAt()).isEqualTo(Instant.parse("2026-05-14T12:00:00Z"));
        assertThat(saved.targetType()).isEqualTo(TargetType.ROUTE);
        assertThat(saved.targetId()).isEqualTo(targetId);
    }

    @Test
    void process_nullTargetType_andNullTargetId_isAllowed() {
        UUID placementId = UUID.randomUUID();
        when(repository.save(any(AdImpressionEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new RecordImpressionCommand(
                placementId.toString(), null, null
        ))).verifyComplete();
    }
}
