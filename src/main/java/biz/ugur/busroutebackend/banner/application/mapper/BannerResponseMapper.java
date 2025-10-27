package biz.ugur.busroutebackend.banner.application.mapper;

import biz.ugur.busroutebackend.banner.application.compresor.DataCompressor;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class BannerResponseMapper {

    private final DataCompressor dataCompressor;

    public BannerResponseMapper(DataCompressor dataCompressor) {
        this.dataCompressor = dataCompressor;
    }

    public Mono<BannerResponse> toResponse(Banner banner) {
        BannerPeriod period = banner.getPeriod();

        return dataCompressor.decodeAndDecompress(banner.getContent())
                .map(decompressedContent -> new BannerResponse(
                        banner.getId().getValue(),
                        banner.getTitle().getValue(),
                        banner.getType().getValue(),
                        banner.getImageUrl().getValue(),
                        banner.getTargetUrl(),
                        banner.getIsActive(),
                        banner.getDisplayOrder(),
                        period.getStartTime(),
                        period.getEndTime(),
                        decompressedContent
                ));
    }
}
