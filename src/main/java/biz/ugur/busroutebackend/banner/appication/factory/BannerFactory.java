package biz.ugur.busroutebackend.banner.appication.factory;

import biz.ugur.busroutebackend.banner.appication.compresor.DataCompressor;
import biz.ugur.busroutebackend.banner.appication.dto.CreateBannerCommand;
import biz.ugur.busroutebackend.banner.appication.dto.UpdateBannerCommand;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
public class BannerFactory {

    private final DataCompressor dataCompressor;

    public BannerFactory(DataCompressor dataCompressor) {
        this.dataCompressor = dataCompressor;
    }

    public Mono<Banner> create(CreateBannerCommand command, String processedImageUrl) {
        Mono<String> compressedContent = command.content() != null
                ? dataCompressor.compressAndEncode(command.content())
                : Mono.empty();

        return compressedContent
                .defaultIfEmpty(null)
                .map(content ->
                Banner.create(
                        BannerTitle.of(command.title()),
                        BannerType.fromValue(command.type()),
                        BannerPeriod.between(command.startDate(), command.endDate()),
                        BannerImage.of(processedImageUrl),
                        command.targetUrl(),
                        command.displayOrder(),
                        content
                )
        );
    }

    public Mono<Banner> update(Banner banner, UpdateBannerCommand command, String processedImageUrl) {
        Mono<String> compressedContent = command.content() != null
                ? dataCompressor.compressAndEncode(command.content())
                : Mono.justOrEmpty(banner.getContent());

        return compressedContent
                .defaultIfEmpty(banner.getContent())
                .map(content -> {
            banner.updateBanner(
                    BannerTitle.of(command.title()),
                    BannerType.fromValue(command.type()),
                    BannerPeriod.between(command.startDate(), command.endDate()),
                    BannerImage.of(processedImageUrl),
                    command.targetUrl(),
                    command.displayOrder(),
                    content
            );

            if (command.isActive()) {
                banner.activate();
            } else {
                banner.deactivate();
            }

            return banner;
        });
    }
}
