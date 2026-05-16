package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateAdPlacementCommandValidationTest {

    private CreateAdPlacementCommand build(PlacementKind kind, PaymentMethod method, String provider) {
        boolean commercial = kind == PlacementKind.COMMERCIAL;
        return new CreateAdPlacementCommand(
                commercial ? "biz-001" : null,
                commercial ? "tariff-001" : null,
                "BANNER",
                kind != null ? kind.name() : null,
                "Test Title",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0,
                method,
                provider,
                ContentType.LINK
        );
    }

    @Test
    void commercialWithoutPaymentMethod_throws() {
        var cmd = build(PlacementKind.COMMERCIAL, null, null);
        assertThrows(IllegalArgumentException.class, cmd::validatePaymentConsistency);
    }

    @Test
    void editorialWithPaymentMethod_throws() {
        var cmd = build(PlacementKind.EDITORIAL, PaymentMethod.CASH, null);
        assertThrows(IllegalArgumentException.class, cmd::validatePaymentConsistency);
    }

    @Test
    void bankWithoutProvider_throws() {
        var cmd = build(PlacementKind.COMMERCIAL, PaymentMethod.BANK, null);
        assertThrows(IllegalArgumentException.class, cmd::validatePaymentConsistency);
    }

    @Test
    void cashWithProvider_throws() {
        var cmd = build(PlacementKind.COMMERCIAL, PaymentMethod.CASH, "RYSGAL");
        assertThrows(IllegalArgumentException.class, cmd::validatePaymentConsistency);
    }

    @Test
    void cashWithoutProvider_ok() {
        var cmd = build(PlacementKind.COMMERCIAL, PaymentMethod.CASH, null);
        assertDoesNotThrow(cmd::validatePaymentConsistency);
    }

    @Test
    void bankWithProvider_ok() {
        var cmd = build(PlacementKind.COMMERCIAL, PaymentMethod.BANK, "RYSGAL");
        assertDoesNotThrow(cmd::validatePaymentConsistency);
    }

    @Test
    void editorialWithoutPayment_ok() {
        var cmd = build(PlacementKind.EDITORIAL, null, null);
        assertDoesNotThrow(cmd::validatePaymentConsistency);
    }
}
