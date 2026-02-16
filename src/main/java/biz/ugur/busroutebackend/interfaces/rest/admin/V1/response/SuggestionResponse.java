package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response;

import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SuggestionResponse(
        String id,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") String entityId,
        @JsonProperty("entity_name") String entityName,
        @JsonProperty("suggested_alias") String suggestedAlias,
        String language,
        String status,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("reviewer_id") String reviewerId,
        @JsonProperty("review_comment") String reviewComment,
        @JsonProperty("created_at") LocalDateTime createdAt
) {

    public static SuggestionResponse fromResult(SuggestionResult result) {
        return new SuggestionResponse(
                result.id(),
                result.entityType(),
                result.entityId(),
                result.entityName(),
                result.suggestedAlias(),
                result.language(),
                result.status(),
                result.clientId(),
                result.reviewerId(),
                result.reviewComment(),
                result.createdAt()
        );
    }
}
