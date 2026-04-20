package biz.ugur.busroutebackend.banner.application.mapper;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import biz.ugur.busroutebackend.shared.application.compressor.DataCompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class BannerResponseMapperTest {

    @InjectMocks
    private BannerResponseMapper mapper;

    @Mock
    private DataCompressor dataCompressor;

    @Test
    void toResponseDecodesContentAndPopulatesFields() {
        Banner banner = Banner.create(
                BannerTitle.of("Sale"),
                BannerType.MAIN,
                BannerPeriod.between(
                        LocalDateTime.of(2026, 4, 20, 0, 0),
                        LocalDateTime.of(2026, 4, 27, 0, 0)),
                BannerImage.of("/banners/sale.jpg"),
                "https://example.com",
                1,
                "COMPRESSED_CONTENT",
                null,
                true
        );

        when(dataCompressor.decodeAndDecompress("COMPRESSED_CONTENT"))
                .thenReturn(Mono.just("<div>Sale banner</div>"));

        StepVerifier.create(mapper.toResponse(banner))
                .assertNext(response -> {
                    assertEquals(banner.getId().getValue(), response.id());
                    assertEquals("Sale", response.title());
                    assertEquals("main", response.type());
                    assertEquals("/banners/sale.jpg", response.imageUrl());
                    assertEquals("https://example.com", response.targetUrl());
                    assertEquals(1, response.displayOrder());
                    assertEquals("<div>Sale banner</div>", response.content());
                })
                .verifyComplete();
    }

    @Test
    void toResponseUsesEmptyContentWhenDecompressorReturnsEmpty() {
        Banner banner = Banner.create(
                BannerTitle.of("Empty content"),
                BannerType.MAIN,
                BannerPeriod.between(
                        LocalDateTime.of(2026, 4, 20, 0, 0),
                        LocalDateTime.of(2026, 4, 27, 0, 0)),
                BannerImage.of("/banners/x.jpg"),
                null, 0, "", null, true
        );

        when(dataCompressor.decodeAndDecompress(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(mapper.toResponse(banner))
                .assertNext(response -> {
                    assertEquals("", response.content());
                    assertNotNull(response.startDate());
                })
                .verifyComplete();
    }
}
