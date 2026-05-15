package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateEditorialAdPlacementCommandTest {

    private UpdateEditorialAdPlacementCommand cmd(ContentType ct, String content, String targetUrl) {
        return new UpdateEditorialAdPlacementCommand(
                "p-1", "title", content, "/img.jpg", targetUrl, "CTA",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                List.of(), 0, ct);
    }

    @Test
    void contentType_required() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> cmd(null, null, "https://e.com").validateContentConsistency());
        assertTrue(ex.getMessage().contains("contentType"));
    }

    @Test
    void content_required_for_content_type() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> cmd(ContentType.CONTENT, "  ", null).validateContentConsistency());
        assertTrue(ex.getMessage().contains("content required"));
    }

    @Test
    void target_url_required_for_link_type() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> cmd(ContentType.LINK, null, null).validateContentConsistency());
        assertTrue(ex.getMessage().contains("targetUrl required"));
    }

    @Test
    void content_forbidden_for_link_type() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> cmd(ContentType.LINK, "<p>x</p>", "https://e.com").validateContentConsistency());
        assertTrue(ex.getMessage().contains("content not allowed"));
    }

    @Test
    void valid_link_passes() {
        cmd(ContentType.LINK, null, "https://e.com").validateContentConsistency();
    }

    @Test
    void valid_content_passes() {
        cmd(ContentType.CONTENT, "<p>x</p>", null).validateContentConsistency();
    }
}
