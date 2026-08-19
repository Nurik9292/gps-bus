package biz.ugur.busroutebackend.advertising.application.usecase.integration;

import biz.ugur.busroutebackend.advertising.application.dto.integration.ExternalBannerCommand;
import biz.ugur.busroutebackend.advertising.application.processor.AdPlacementImageProcessor;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.utility.BannerTypeTargetTypeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class UpsertExternalBannerUseCase extends BaseUseCase<Mono<ExternalBannerCommand>, AdPlacement> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private static final String EMBEDDED_IMAGE_PREFIX = "data:image/";

    private final AdPlacementRepository placementRepository;
    private final AdPlacementTargetRepository targetRepository;
    private final AdPlacementImageProcessor imageProcessor;
    private final SecurityContextService securityService;

    public UpsertExternalBannerUseCase(AdPlacementRepository placementRepository,
                                       AdPlacementTargetRepository targetRepository,
                                       AdPlacementImageProcessor imageProcessor,
                                       SecurityContextService securityService,
                                       CorrelationContextService correlationService,
                                       EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.targetRepository = targetRepository;
        this.imageProcessor = imageProcessor;
        this.securityService = securityService;
    }

    @Override
    protected String getBoundContext() {
        return "advertising";
    }

    @Override
    protected Mono<AdPlacement> process(Mono<ExternalBannerCommand> request) {
        return request.flatMap(this::upsert);
    }

    private Mono<AdPlacement> upsert(ExternalBannerCommand command) {
        TargetType targetType = resolveRoutesTarget(command.type());
        requireEmbeddedImage(command.imageUrl());

        return placementRepository.findByExternalRef(command.externalServiceId(), command.externalRef())
                .flatMap(existing -> rejectForeign(existing, command))
                .flatMap(existing -> rejectFinishedRun(existing, command))
                .flatMap(existing -> imageProcessor.processForUpdate(command.imageUrl(), existing.getImageUrl())
                        .map(storedImage -> applyUpdate(existing, command, targetType, storedImage)))
                .switchIfEmpty(Mono.defer(() -> imageProcessor.process(command.imageUrl())
                        .map(storedImage -> putOnAir(buildNew(command, targetType, storedImage)))))
                .flatMap(placementRepository::save)
                .flatMap(saved -> targetRepository
                        .replaceAll(saved.getId(), List.of(PlacementTarget.general(targetType)))
                        .thenReturn(saved))
                .flatMap(saved -> auditOperation(saved, command).thenReturn(saved));
    }

    private static void requireEmbeddedImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(EMBEDDED_IMAGE_PREFIX)) {
            throw new AdvertisingValidationException("image",
                    "banner image must be sent as base64 data URL (data:image/...), links to external storage are not accepted");
        }
    }

    private TargetType resolveRoutesTarget(String rawType) {
        BannerType bannerType = BannerType.fromValue(rawType);
        if (bannerType != BannerType.ROUTES) {
            throw new AdvertisingValidationException("type",
                    "external service may send only banners of type routes, got: " + rawType);
        }
        return BannerTypeTargetTypeMapper.toTarget(bannerType);
    }

    private Mono<AdPlacement> rejectForeign(AdPlacement existing, ExternalBannerCommand command) {
        if (!existing.isOwnedBy(command.externalServiceId())) {
            return Mono.error(new AdvertisingValidationException("externalRef",
                    "placement belongs to another owner"));
        }
        return Mono.just(existing);
    }

    private Mono<AdPlacement> rejectFinishedRun(AdPlacement existing, ExternalBannerCommand command) {
        if (existing.getStatus() == PlacementStatus.EXPIRED || existing.getStatus() == PlacementStatus.CANCELLED) {
            return Mono.error(new AdvertisingValidationException("externalRef",
                    "banner '" + command.externalRef() + "' has already finished its run ("
                            + existing.getStatus() + "); send a new run under a different externalRef"));
        }
        return Mono.just(existing);
    }

    private static AdPlacement putOnAir(AdPlacement placement) {
        AdPlacement scheduled = placement.markAsPendingPayment().markAsScheduled();
        return windowAlreadyOpen(scheduled) ? scheduled.markAsActive() : scheduled;
    }

    private static boolean windowAlreadyOpen(AdPlacement placement) {
        LocalDateTime startsAt = placement.getWindow() != null ? placement.getWindow().getStartsAt() : null;
        return startsAt == null || !startsAt.isAfter(LocalDateTime.now(ASHGABAT_ZONE));
    }

    private AdPlacement buildNew(ExternalBannerCommand command, TargetType targetType, String storedImage) {
        return AdPlacement.createExternal(
                command.externalServiceId(),
                command.externalRef(),
                PlacementType.BANNER,
                command.title(),
                command.content(),
                storedImage,
                command.targetUrl(),
                null,
                contentTypeOf(command),
                PlacementWindow.of(command.startsAt(), command.endsAt()),
                List.of(PlacementTarget.general(targetType)),
                command.displayOrder());
    }

    private AdPlacement applyUpdate(AdPlacement existing, ExternalBannerCommand command,
                                    TargetType targetType, String storedImage) {
        return existing.toBuilder()
                .title(command.title().trim())
                .content(command.content())
                .imageUrl(storedImage)
                .targetUrl(command.targetUrl())
                .contentType(contentTypeOf(command))
                .window(PlacementWindow.of(command.startsAt(), command.endsAt()))
                .targets(List.of(PlacementTarget.general(targetType)))
                .displayOrder(command.displayOrder() != null ? command.displayOrder() : existing.getDisplayOrder())
                .build();
    }

    private static ContentType contentTypeOf(ExternalBannerCommand command) {
        return command.content() != null && !command.content().isBlank()
                ? ContentType.CONTENT
                : ContentType.LINK;
    }

    private Mono<Void> auditOperation(AdPlacement placement, ExternalBannerCommand command) {
        log.info("[ExternalBanner] service={} ref={} placement={} title={}",
                command.externalServiceId(), command.externalRef(),
                placement.getId().getValue(), placement.getTitle());
        return securityService.logAudit("EXTERNAL_BANNER_UPSERT",
                "placement:" + placement.getId().getValue(),
                command.externalServiceId());
    }
}
