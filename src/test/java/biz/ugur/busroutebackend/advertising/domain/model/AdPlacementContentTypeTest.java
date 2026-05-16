package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdPlacementContentTypeTest {

    private static final BusinessId BUSINESS = BusinessId.of("b-1");
    private static final TariffId   TARIFF   = TariffId.of("t-1");
    private static final List<PlacementTarget> NO_TARGETS = List.of();

    @Test
    void content_type_link_with_target_url_is_valid() {
        AdPlacement p = AdPlacement.create(BUSINESS, TARIFF, PlacementType.BANNER,
                PlacementKind.COMMERCIAL, "title", null, "/img.jpg",
                "https://example.com", "CTA",
                ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0);
        assertEquals(ContentType.LINK, p.getContentType());
    }

    @Test
    void content_type_link_without_target_url_throws() {
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                AdPlacement.create(BUSINESS, TARIFF, PlacementType.BANNER,
                        PlacementKind.COMMERCIAL, "title", null, "/img.jpg",
                        null, "CTA",
                        ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("targetUrl", ex.getFailedField());
    }

    @Test
    void content_type_link_with_content_throws() {
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                AdPlacement.create(BUSINESS, TARIFF, PlacementType.BANNER,
                        PlacementKind.COMMERCIAL, "title", "<h1>nope</h1>", "/img.jpg",
                        "https://example.com", "CTA",
                        ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("content", ex.getFailedField());
    }

    @Test
    void content_type_content_with_content_is_valid() {
        AdPlacement p = AdPlacement.create(null, null, PlacementType.BANNER,
                PlacementKind.EDITORIAL, "title", "<h1>hi</h1>", "/img.jpg",
                null, null,
                ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0);
        assertEquals(ContentType.CONTENT, p.getContentType());
        assertEquals("<h1>hi</h1>", p.getContent());
    }

    @Test
    void content_type_content_without_content_throws() {
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                AdPlacement.create(null, null, PlacementType.BANNER,
                        PlacementKind.EDITORIAL, "title", "  ", "/img.jpg",
                        null, null,
                        ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("content", ex.getFailedField());
    }

    @Test
    void editorial_without_business_or_tariff_is_valid() {
        AdPlacement p = AdPlacement.create(null, null, PlacementType.BANNER,
                PlacementKind.EDITORIAL, "title", "<h1>hi</h1>", "/img.jpg",
                null, null,
                ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0);
        assertNull(p.getBusinessId());
        assertNull(p.getTariffId());
    }

    @Test
    void commercial_without_business_throws() {
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                AdPlacement.create(null, TARIFF, PlacementType.BANNER,
                        PlacementKind.COMMERCIAL, "title", null, "/img.jpg",
                        "https://example.com", "CTA",
                        ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("businessId", ex.getFailedField());
    }

    @Test
    void commercial_without_tariff_throws() {
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                AdPlacement.create(BUSINESS, null, PlacementType.BANNER,
                        PlacementKind.COMMERCIAL, "title", null, "/img.jpg",
                        "https://example.com", "CTA",
                        ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("tariffId", ex.getFailedField());
    }
}
