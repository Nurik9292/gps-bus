package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.PlacementTargetSpec;
import biz.ugur.busroutebackend.advertising.application.dto.UpdateEditorialAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.application.processor.AdPlacementImageProcessor;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class UpdateEditorialAdPlacementUseCase
        extends BaseUseCase<Mono<UpdateEditorialAdPlacementCommand>, AdPlacementResponse> {

    private final AdPlacementRepository placementRepository;
    private final AdPlacementTargetRepository targetRepository;
    private final SecurityContextService securityService;
    private final AdPlacementResponseMapper responseMapper;
    private final AdPlacementImageProcessor imageProcessor;

    public UpdateEditorialAdPlacementUseCase(AdPlacementRepository placementRepository,
                                              AdPlacementTargetRepository targetRepository,
                                              SecurityContextService securityService,
                                              AdPlacementResponseMapper responseMapper,
                                              AdPlacementImageProcessor imageProcessor,
                                              CorrelationContextService correlationService,
                                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.targetRepository = targetRepository;
        this.securityService = securityService;
        this.responseMapper = responseMapper;
        this.imageProcessor = imageProcessor;
    }

    @Override
    protected Mono<AdPlacementResponse> process(Mono<UpdateEditorialAdPlacementCommand> request) {
        return request.flatMap(cmd -> {
            cmd.validateContentConsistency();
            PlacementId id = PlacementId.of(cmd.placementId());
            return placementRepository.findById(id)
                    .switchIfEmpty(Mono.defer(() ->
                            Mono.error(new AdPlacementNotFoundException(cmd.placementId()))))
                    .flatMap(existing -> applyAndPersist(existing, cmd));
        });
    }

    private Mono<AdPlacementResponse> applyAndPersist(AdPlacement existing,
                                                       UpdateEditorialAdPlacementCommand cmd) {
        return imageProcessor.processForUpdate(cmd.imageUrl(), existing.getImageUrl())
                .defaultIfEmpty("")
                .flatMap(resolvedImageUrl -> {
                    String finalImageUrl = resolvedImageUrl.isEmpty() ? null : resolvedImageUrl;
                    List<PlacementTarget> domainTargets = toDomainTargets(cmd.targets());
                    PlacementWindow window = PlacementWindow.of(cmd.startsAt(), cmd.endsAt());
                    AdPlacement updated = existing.updateEditorialContent(
                            cmd.title(), cmd.content(), finalImageUrl, cmd.targetUrl(),
                            cmd.ctaText(), cmd.contentType(), window, domainTargets, cmd.displayOrder());
                    return placementRepository.save(updated)
                            .flatMap(saved -> targetRepository.replaceAll(saved.getId(), saved.getTargets())
                                    .then(publishEvents(saved))
                                    .then(responseMapper.toResponse(saved)));
                });
    }

    private Mono<Void> publishEvents(AdPlacement aggregate) {
        aggregate.getDomainEvents().forEach(eventBus::publish);
        aggregate.clearEvents();
        return Mono.empty();
    }

    private static List<PlacementTarget> toDomainTargets(List<PlacementTargetSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        return specs.stream()
                .map(s -> PlacementTarget.of(TargetType.from(s.targetType()), s.targetId()))
                .toList();
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
