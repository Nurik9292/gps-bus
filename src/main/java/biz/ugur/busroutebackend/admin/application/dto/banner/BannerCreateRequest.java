package biz.ugur.busroutebackend.admin.application.dto.banner;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    @JsonProperty("title")
    private String title;

    @NotBlank(message = "Image URL is required")
    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("target_url")
    private String targetUrl;

    @JsonProperty("display_order")
    private Integer displayOrder = 0;

    @JsonProperty("end_date")
    private LocalDateTime endDate;
}
