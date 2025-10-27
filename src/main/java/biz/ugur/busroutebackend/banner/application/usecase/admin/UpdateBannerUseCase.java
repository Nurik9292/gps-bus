package biz.ugur.busroutebackend.banner.application.usecase.admin;

import biz.ugur.busroutebackend.banner.application.dto.UpdateBannerCommand;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.application.factory.BannerFactory;
import biz.ugur.busroutebackend.banner.application.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.application.processor.BannerImageProcessor;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateBannerUseCase extends BaseUseCase<Mono<UpdateBannerCommand>, BannerResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerImageProcessor bannerImageProcessor;
    private final BannerFactory bannerFactory;
    private final BannerResponseMapper bannerResponseMapper;

    public UpdateBannerUseCase(AdminBannerRepository bannerRepository,
                               EventBus eventBus,
                               CorrelationContextService correlationContextService,
                               BannerImageProcessor bannerImageProcessor,
                               BannerFactory bannerFactory,
                               BannerResponseMapper bannerResponseMapper) {
        super(correlationContextService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerImageProcessor = bannerImageProcessor;
        this.bannerFactory = bannerFactory;
        this.bannerResponseMapper = bannerResponseMapper;
    }


    @Override
    protected Mono<BannerResponse> process(Mono<UpdateBannerCommand> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerResponse> processInternal(UpdateBannerCommand command) {
        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId -> log.info(
                        "Updating banner: CorrelationId - {} BannerId - {}", correlationId, command.id()
                ))
                .then(bannerRepository.findById(BannerId.of(command.id())))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Banner not found: " + command.id())))
                .flatMap(banner ->
                        bannerImageProcessor.processForUpdate(command.imageUrl(), banner.getImageUrl().getValue())
                                .flatMap(processedImageUrl -> bannerFactory.update(banner, command, processedImageUrl))
                )
                .flatMap(bannerRepository::save)
                .flatMap(bannerResponseMapper::toResponse)
                .doOnSuccess(response -> log.info("Banner updated successfully: {}", response.title()))
                .doOnError(error -> log.error("Failed to update banner: {}", command.id(), error));
    }

}
