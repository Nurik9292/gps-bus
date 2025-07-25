package biz.ugur.busroutebackend.admin.application.dto.banner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("target_url")
    private String targetUrl;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("display_order")
    private Integer displayOrder;

    @JsonProperty("start_date")
    private LocalDateTime startDate;

    @JsonProperty("end_date")
    private LocalDateTime endDate;

     public BannerResponse(String id,
                           String title,
                           String imageUrl,
                           String targetUrl,
                           Boolean isActive,
                           Integer displayOrder,
                           LocalDateTime startDate,
                           LocalDateTime endDate) {
         this.id = id;
         this.title = title;
         this.imageUrl = imageUrl;
         this.targetUrl = targetUrl;
         this.isActive = isActive;
         this.displayOrder = displayOrder;
         this.startDate = startDate;
         this.endDate = endDate;
     }
}
