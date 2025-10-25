package biz.ugur.busroutebackend.banner.appication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
         this.startDate = startDate != null
             ? startDate.atZone(ZoneId.systemDefault()).toInstant()
             : null;
         this.endDate = endDate != null
             ? endDate.atZone(ZoneId.systemDefault()).toInstant()
             : null;
         this.content = content;
     }
}
