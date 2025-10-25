package biz.ugur.busroutebackend.banner.appication.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CreateBannerCommand (String title,
                                   String type,
                                   String imageUrl,
                                   String targetUrl,
                                   Integer displayOrder,
                                   LocalDateTime endDate,
                                   LocalDateTime startDate,
                                   String content) {

}
