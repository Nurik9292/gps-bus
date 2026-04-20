package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdTariffResponseMapperTest {

    private final AdTariffResponseMapper mapper = new AdTariffResponseMapper();

    @Test
    void toResponseCopiesTariffFields() {
        AdTariff tariff = AdTariff.create(
                TariffName.of("Premium"), "desc",
                PlacementType.BANNER, TariffPeriod.WEEK,
                Price.ofMinor(100_00L, "TMT"),
                1000, 100, 50, 2);

        StepVerifier.create(mapper.toResponse(tariff))
                .assertNext(response -> {
                    assertEquals(tariff.getId().getValue(), response.id());
                    assertEquals("Premium", response.name());
                    assertEquals("desc", response.description());
                    assertEquals(100_00L, response.priceAmount());
                    assertEquals("TMT", response.currency());
                })
                .verifyComplete();
    }
}
