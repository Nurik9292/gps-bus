package biz.ugur.busroutebackend.banner.appication.usecase.admin;

import biz.ugur.busroutebackend.banner.appication.dto.CreateBannerCommand;
import biz.ugur.busroutebackend.banner.appication.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.appication.factory.BannerFactory;
import biz.ugur.busroutebackend.banner.appication.mapper.BannerResponseMapper;
import biz.ugur.busroutebackend.banner.appication.processor.BannerImageProcessor;
import biz.ugur.busroutebackend.banner.domain.repository.AdminBannerRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateBannerUseCase extends BaseUseCase<Mono<CreateBannerCommand>, BannerResponse> {

    private final AdminBannerRepository bannerRepository;
    private final BannerFactory bannerFactory;
    private final BannerImageProcessor bannerImageProcessor;
    private final BannerResponseMapper bannerResponseMapper;

    public CreateBannerUseCase(AdminBannerRepository bannerRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus,
                               BannerFactory bannerFactory,
                               BannerImageProcessor bannerImageProcessor,
                               BannerResponseMapper bannerResponseMapper) {
        super(correlationService, eventBus);
        this.bannerRepository = bannerRepository;
        this.bannerImageProcessor = bannerImageProcessor;
        this.bannerFactory = bannerFactory;
        this.bannerResponseMapper = bannerResponseMapper;
    }


    @Override
    protected Mono<BannerResponse> process(Mono<CreateBannerCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<BannerResponse> processInternal(CreateBannerCommand command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating new banner: CorrelationId - {} BannerTitle  {}", correlationId, command.title());

            return bannerImageProcessor.process(command.imageUrl())
                    .flatMap(processedImageUrl -> bannerFactory.create(command, processedImageUrl))
                    .flatMap(bannerRepository::save)
                    .flatMap(bannerResponseMapper::toResponse)
                    .doOnSuccess(response -> log.info("Banner created successfully: {}", response.getTitle()))
                    .doOnError(error -> log.error("Failed to create banner: {}", command.title(), error));
        });
    }
}