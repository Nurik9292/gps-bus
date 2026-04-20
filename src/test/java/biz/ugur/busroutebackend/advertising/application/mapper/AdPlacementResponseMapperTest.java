package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdPlacementResponseMapperTest {

    private final AdPlacementResponseMapper mapper = new AdPlacementResponseMapper();

    @Test
    void toResponseCopiesPlacementFields() {
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                "My Ad", "Body", "/img.jpg", "https://x.tm", "Click",
                null, List.of("home"), 1);

        StepVerifier.create(mapper.toResponse(placement))
                .assertNext(response -> {
                    assertEquals(placement.getId().getValue(), response.id());
                    assertEquals("My Ad", response.title());
                    assertEquals("Body", response.content());
                    assertEquals("https://x.tm", response.targetUrl());
                    assertEquals("Click", response.ctaText());
                    assertNotNull(response.status());
                })
                .verifyComplete();
    }
}
