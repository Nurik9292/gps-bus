package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.banner;

import biz.ugur.busroutebackend.banner.application.dto.UpdateBannerCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;


@Data
public class BannerUpdateRequest {

    @JsonProperty("title")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @JsonProperty("type")
    @Size(max = 100, message = "Type cannot exceed 100 characters")
    private String type;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("target_url")
    private String targetUrl;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("display_order")
    private Integer displayOrder;

    @JsonProperty("end_date")
    private LocalDateTime endDate;

    @JsonProperty("start_date")
    private LocalDateTime startDate;

    @JsonProperty("content")
    private String content;

    public UpdateBannerCommand  toCommand(String id) {
        return UpdateBannerCommand.builder()
                .id(id)
                .title(this.title)
                .type(this.type)
                .imageUrl(this.imageUrl)
                .targetUrl(this.targetUrl)
                .isActive(this.isActive)
                .displayOrder(this.displayOrder)
                .endDate(this.endDate)
                .startDate(this.startDate)
                .content(this.content)
                .build();
    }
}