package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementUpdatedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.exceptions.PlacementStateTransitionException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdPlacementUpdateEditorialTest {

    private static final List<PlacementTarget> NO_TARGETS = List.of();

    private AdPlacement editorialActive() {
        return AdPlacement.create(null, null, PlacementType.BANNER,
                        PlacementKind.EDITORIAL, "old title", "<p>old</p>", "/old.jpg",
                        null, null, ContentType.CONTENT,
                        PlacementWindow.unscheduled(), NO_TARGETS, 0)
                .approve("admin")
                .markAsScheduled()
                .markAsActive();
    }

    @Test
    void update_changes_title_content_image_displayOrder_and_registers_event() {
        AdPlacement before = editorialActive();
        before.clearEvents();
        AdPlacement after = before.updateEditorialContent(
                "new title", "<p>new</p>", "/new.jpg", null, "Перейти",
                ContentType.CONTENT,
                PlacementWindow.of(LocalDateTime.of(2026, 5, 16, 0, 0), null),
                NO_TARGETS, 5);

        assertEquals("new title", after.getTitle());
        assertEquals("<p>new</p>", after.getContent());
        assertEquals("/new.jpg", after.getImageUrl());
        assertEquals("Перейти", after.getCtaText());
        assertEquals(5, after.getDisplayOrder());
        assertEquals(1, after.getDomainEvents().size());
        assertInstanceOf(AdPlacementUpdatedEvent.class, after.getDomainEvents().get(0));
    }

    @Test
    void update_link_type_clears_content() {
        AdPlacement before = editorialActive();
        AdPlacement after = before.updateEditorialContent(
                "t", null, "/img.jpg", "https://example.com", "Open",
                ContentType.LINK,
                PlacementWindow.unscheduled(), NO_TARGETS, 0);
        assertEquals(ContentType.LINK, after.getContentType());
        assertEquals("https://example.com", after.getTargetUrl());
        assertNull(after.getContent());
    }

    @Test
    void update_commercial_throws() {
        AdPlacement commercial = AdPlacement.create(
                biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId.of("b1"),
                biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId.of("t1"),
                PlacementType.BANNER, PlacementKind.COMMERCIAL,
                "t", null, "/img.jpg", "https://e.com", "C",
                ContentType.LINK,
                PlacementWindow.unscheduled(), NO_TARGETS, 0);
        var ex = assertThrows(IllegalStateException.class, () ->
                commercial.updateEditorialContent("t2", null, "/img2.jpg", "https://e.com",
                        "C", ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertTrue(ex.getMessage().contains("EDITORIAL"));
    }

    @Test
    void update_expired_throws() {
        AdPlacement expired = editorialActive().markAsExpired();
        var ex = assertThrows(PlacementStateTransitionException.class, () ->
                expired.updateEditorialContent("t", "<p>c</p>", "/i.jpg", null, null,
                        ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertTrue(ex.getMessage().toLowerCase().contains("expired")
                || ex.getMessage().contains("EXPIRED"));
    }

    @Test
    void update_cancelled_throws() {
        AdPlacement cancelled = editorialActive().cancel();
        assertThrows(PlacementStateTransitionException.class, () ->
                cancelled.updateEditorialContent("t", "<p>c</p>", "/i.jpg", null, null,
                        ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0));
    }

    @Test
    void update_content_type_content_without_content_throws() {
        AdPlacement before = editorialActive();
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                before.updateEditorialContent("t", "  ", "/i.jpg", null, null,
                        ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("content", ex.getFailedField());
    }

    @Test
    void update_content_type_link_without_target_url_throws() {
        AdPlacement before = editorialActive();
        var ex = assertThrows(AdvertisingValidationException.class, () ->
                before.updateEditorialContent("t", null, "/i.jpg", null, null,
                        ContentType.LINK, PlacementWindow.unscheduled(), NO_TARGETS, 0));
        assertEquals("targetUrl", ex.getFailedField());
    }

    @Test
    void update_event_contains_changed_fields() {
        AdPlacement before = editorialActive();
        before.clearEvents();
        AdPlacement after = before.updateEditorialContent(
                "new title", "<p>new</p>", "/new.jpg", null, null,
                ContentType.CONTENT, PlacementWindow.unscheduled(), NO_TARGETS, 0);
        AdPlacementUpdatedEvent ev = (AdPlacementUpdatedEvent) after.getDomainEvents().get(0);
        assertEquals(after.getId().getValue(), ev.getAggregateId());
        assertTrue(ev.getChanges().containsKey("title"));
        assertTrue(ev.getChanges().containsKey("content"));
        assertTrue(ev.getChanges().containsKey("imageUrl"));
    }
}
