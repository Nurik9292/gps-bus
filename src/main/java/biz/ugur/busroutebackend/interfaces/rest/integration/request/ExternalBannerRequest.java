package biz.ugur.busroutebackend.interfaces.rest.integration.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ExternalBannerRequest(
        @NotBlank @Size(max = 100) @JsonProperty("external_ref") String externalRef,
        @NotBlank @Size(max = 20) String type,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @JsonProperty("image_url") String imageUrl,
        @JsonProperty("target_url") String targetUrl,
        String content,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") @JsonProperty("starts_at") LocalDateTime startsAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") @JsonProperty("ends_at") LocalDateTime endsAt,
        @JsonProperty("display_order") Integer displayOrder) {
}
