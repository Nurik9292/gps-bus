package biz.ugur.busroutebackend.advertising.application.dto.integration;

import java.time.LocalDateTime;

public record ExternalBannerCommand(
        String externalServiceId,
        String externalRef,
        String type,
        String title,
        String imageUrl,
        String targetUrl,
        String content,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer displayOrder
) {
}
