package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.UpdateEditorialAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateEditorialAdPlacementUseCaseTest {

    @Mock private AdPlacementRepository placementRepository;
    @Mock private AdPlacementTargetRepository targetRepository;
    @Mock private SecurityContextService securityService;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;
    @Mock private AdPlacementResponseMapper responseMapper;

    @InjectMocks private UpdateEditorialAdPlacementUseCase useCase;

    @BeforeEach
    void setup() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void updates_editorial_placement_and_returns_response() {
        AdPlacement existing = AdPlacement.create(null, null, PlacementType.BANNER,
                        PlacementKind.EDITORIAL, "old title", "<p>old</p>", "/old.jpg",
                        null, null, ContentType.CONTENT,
                        PlacementWindow.unscheduled(), List.of(), 0)
                .approve("admin").markAsScheduled().markAsActive();

        when(placementRepository.findById(any(PlacementId.class))).thenReturn(Mono.just(existing));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(targetRepository.replaceAll(any(PlacementId.class), any())).thenReturn(Mono.empty());
        when(responseMapper.toResponse(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(AdPlacementResponse.fromDomain(inv.getArgument(0))));

        UpdateEditorialAdPlacementCommand cmd = new UpdateEditorialAdPlacementCommand(
                existing.getId().getValue(), "new title", "<p>new</p>", "/new.jpg", null, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                List.of(), 1, ContentType.CONTENT);

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(resp -> {
                    assertEquals("new title", resp.title());
                    assertEquals("<p>new</p>", resp.content());
                    assertEquals(1, resp.displayOrder());
                })
                .verifyComplete();
    }

    @Test
    void not_found_returns_error() {
        when(placementRepository.findById(any(PlacementId.class))).thenReturn(Mono.empty());

        UpdateEditorialAdPlacementCommand cmd = new UpdateEditorialAdPlacementCommand(
                "missing", "t", "<p>x</p>", "/i.jpg", null, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                List.of(), 0, ContentType.CONTENT);

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(AdPlacementNotFoundException.class, err))
                .verify();
    }
}
