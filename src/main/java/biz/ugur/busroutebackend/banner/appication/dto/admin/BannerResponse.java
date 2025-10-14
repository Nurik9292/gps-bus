package biz.ugur.busroutebackend.banner.appication.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
public class BannerResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("type")
    private String type;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("target_url")
    private String targetUrl;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("display_order")
    private Integer displayOrder;

    @JsonProperty("start_date")
    private Instant startDate;

    @JsonProperty("end_date")
    private Instant endDate;

    @JsonProperty("content")
    private String content;

     public BannerResponse(String id,
                           String title,
                           String type,
                           String imageUrl,
                           String targetUrl,
                           Boolean isActive,
                           Integer displayOrder,
                           LocalDateTime startDate,
                           LocalDateTime endDate,
                           String content) {
         this.id = id;
         this.title = title;
         this.type = type;
         this.imageUrl = imageUrl;
         this.targetUrl = targetUrl;
         this.isActive = isActive;
         this.displayOrder = displayOrder;
         this.startDate = Instant.ofEpochSecond(startDate.getSecond());
         this.endDate = Instant.ofEpochSecond(endDate.getSecond());
         this.content = content;
     }
}
