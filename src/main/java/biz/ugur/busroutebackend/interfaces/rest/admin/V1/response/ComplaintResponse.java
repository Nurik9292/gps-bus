package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response;

import biz.ugur.busroutebackend.complaint.application.dto.ComplaintResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ComplaintResponse(
        String id,
        String type,
        String title,
        String description,
        String status,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("admin_comment") String adminComment,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {

    public static ComplaintResponse fromResult(ComplaintResult result) {
        return new ComplaintResponse(
                result.id(),
                result.type(),
                result.title(),
                result.description(),
                result.status(),
                result.clientId(),
                result.adminComment(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
