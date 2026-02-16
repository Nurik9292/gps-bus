package biz.ugur.busroutebackend.suggestion.application.dto;

import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;

import java.time.LocalDateTime;

public record SuggestionResult(
        String id,
        String entityType,
        String entityId,
        String entityName,
        String suggestedAlias,
        String language,
        String status,
        String clientId,
        String reviewerId,
        String reviewComment,
        LocalDateTime createdAt
) {

    public static SuggestionResult fromDomain(AliasSuggestion suggestion) {
        return new SuggestionResult(
                suggestion.getId().getValue(),
                suggestion.getEntityType().name(),
                suggestion.getEntityId(),
                null,
                suggestion.getSuggestedAlias(),
                suggestion.getLanguage(),
                suggestion.getStatus().name(),
                suggestion.getClientId(),
                suggestion.getReviewerId(),
                suggestion.getReviewComment(),
                suggestion.getCreatedAt()
        );
    }

    public SuggestionResult withEntityName(String name) {
        return new SuggestionResult(
                id, entityType, entityId, name, suggestedAlias,
                language, status, clientId, reviewerId, reviewComment, createdAt
        );
    }
}
