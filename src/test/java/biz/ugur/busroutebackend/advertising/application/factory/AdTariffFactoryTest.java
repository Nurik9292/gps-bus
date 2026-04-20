package biz.ugur.busroutebackend.advertising.application.factory;

import biz.ugur.busroutebackend.advertising.application.dto.CreateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AdTariffFactoryTest {

    private final AdTariffFactory factory = new AdTariffFactory();

    @Test
    void createBuildsTariffFromCommand() {
        CreateAdTariffCommand cmd = new CreateAdTariffCommand(
                "Premium Banner", "desc", "BANNER", "WEEK",
                100_000L, "TMT", 1000, 100, 50, 1);

        StepVerifier.create(factory.create(cmd))
                .assertNext(tariff -> {
                    assertEquals("Premium Banner", tariff.getName().getValue());
                    assertEquals(PlacementType.BANNER, tariff.getPlacementType());
                    assertEquals(TariffPeriod.WEEK, tariff.getPeriod());
                    assertEquals(100_000L, tariff.getPrice().getAmountMinor());
                    assertEquals("TMT", tariff.getPrice().getCurrency());
                })
                .verifyComplete();
    }

    @Test
    void createDefaultsCurrencyToTmtWhenNull() {
        CreateAdTariffCommand cmd = new CreateAdTariffCommand(
                "Basic", null, "PUSH", "MONTH",
                5_000L, null, null, null, null, 0);

        StepVerifier.create(factory.create(cmd))
                .assertNext(tariff -> assertEquals("TMT", tariff.getPrice().getCurrency()))
                .verifyComplete();
    }

    @Test
    void createRejectsNullPriceAmount() {
        CreateAdTariffCommand cmd = new CreateAdTariffCommand(
                "Free", "desc", "POPUP", "DAY",
                null, "TMT", 10, 1, 1, 0);

        StepVerifier.create(factory.create(cmd))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(AdvertisingValidationException.class, err);
                    assertEquals("price_amount", ((AdvertisingValidationException) err).getFailedField());
                })
                .verify();
    }

    @Test
    void createRejectsUnknownPlacementType() {
        CreateAdTariffCommand cmd = new CreateAdTariffCommand(
                "Bad", "desc", "UNKNOWN_TYPE", "DAY",
                100L, "TMT", 1, 1, 1, 0);

        StepVerifier.create(factory.create(cmd))
                .expectError(AdvertisingValidationException.class)
                .verify();
    }
}
