package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateAdPlacementCommandContentValidationTest {

    private CreateAdPlacementCommand cmd(PlacementKind kind, ContentType ct, String content, String targetUrl) {
        return new CreateAdPlacementCommand(
                kind == PlacementKind.COMMERCIAL ? "b-1" : null,
                kind == PlacementKind.COMMERCIAL ? "t-1" : null,
                PlacementType.BANNER.name(),
                kind.name(),
                "title",
                content,
                "/img.jpg",
                targetUrl,
                "CTA",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                List.of(),
                0,
                kind == PlacementKind.COMMERCIAL ? PaymentMethod.CASH : null,
                null,
                ct
        );
    }

    @Test
    void content_required_for_content_type_content() {
        var c = cmd(PlacementKind.EDITORIAL, ContentType.CONTENT, "  ", null);
        var ex = assertThrows(IllegalArgumentException.class, c::validateContentConsistency);
        assertTrue(ex.getMessage().contains("content required"));
    }

    @Test
    void target_url_required_for_content_type_link() {
        var c = cmd(PlacementKind.COMMERCIAL, ContentType.LINK, null, null);
        var ex = assertThrows(IllegalArgumentException.class, c::validateContentConsistency);
        assertTrue(ex.getMessage().contains("targetUrl required"));
    }

    @Test
    void content_not_allowed_for_link() {
        var c = cmd(PlacementKind.COMMERCIAL, ContentType.LINK, "<h1>x</h1>", "https://e.com");
        var ex = assertThrows(IllegalArgumentException.class, c::validateContentConsistency);
        assertTrue(ex.getMessage().contains("content not allowed"));
    }

    @Test
    void content_type_required() {
        var c = cmd(PlacementKind.COMMERCIAL, null, null, "https://e.com");
        var ex = assertThrows(IllegalArgumentException.class, c::validateContentConsistency);
        assertTrue(ex.getMessage().contains("contentType required"));
    }

    @Test
    void editorial_must_not_have_payment_method() {
        var c = new CreateAdPlacementCommand(
                null,
                null,
                PlacementType.BANNER.name(),
                PlacementKind.EDITORIAL.name(),
                "title",
                "<h1>hi</h1>",
                "/img.jpg",
                null,
                "CTA",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                List.of(),
                0,
                PaymentMethod.CASH,
                null,
                ContentType.CONTENT);
        var ex = assertThrows(IllegalArgumentException.class, c::validatePaymentConsistency);
        assertTrue(ex.getMessage().contains("EDITORIAL"));
    }

    @Test
    void commercial_must_have_business_and_tariff() {
        var c = new CreateAdPlacementCommand(
                null,
                null,
                PlacementType.BANNER.name(),
                PlacementKind.COMMERCIAL.name(),
                "title",
                null,
                "/img.jpg",
                "https://e.com",
                "CTA",
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                List.of(),
                0,
                PaymentMethod.CASH,
                null,
                ContentType.LINK);
        var ex = assertThrows(IllegalArgumentException.class, c::validatePaymentConsistency);
        assertTrue(ex.getMessage().contains("COMMERCIAL"));
    }
}
