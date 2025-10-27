package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.banner;

import biz.ugur.busroutebackend.banner.application.dto.CreateBannerCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerCreateRequest {
    @JsonProperty("title")
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @JsonProperty("type")
    @Size(max = 100, message = "Type cannot exceed 100 characters")
    private String type = "main";

    @JsonProperty("imageUrl")
    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    @JsonProperty("targetUrl")
    private String targetUrl;

    @JsonProperty("displayOrder")
    private Integer displayOrder = 0;

    @JsonProperty("endDate")
    private LocalDateTime endDate;

    @JsonProperty("starDate")
    private LocalDateTime startDate;

    @JsonProperty("content")
    String content;

    public CreateBannerCommand toCommand() {
        return CreateBannerCommand.builder()
                .title(this.title)
                .type(this.type)
                .imageUrl(this.imageUrl)
                .targetUrl(this.targetUrl)
                .displayOrder(this.displayOrder)
                .endDate(this.endDate)
                .startDate(this.startDate)
                .content(this.content)
                .build();
    }
}