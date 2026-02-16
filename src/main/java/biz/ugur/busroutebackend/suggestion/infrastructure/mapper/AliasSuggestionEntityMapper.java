package biz.ugur.busroutebackend.suggestion.infrastructure.mapper;

import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionStatus;
import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
import biz.ugur.busroutebackend.suggestion.infrastructure.persistence.entity.AliasSuggestionEntity;

public class AliasSuggestionEntityMapper {

    private AliasSuggestionEntityMapper() {}

    public static AliasSuggestion toDomain(AliasSuggestionEntity entity) {
        AliasSuggestion suggestion = AliasSuggestion.builder()
                .id(AliasSuggestionId.of(entity.getId()))
                .entityType(SuggestionEntityType.valueOf(entity.getEntityType()))
                .entityId(entity.getEntityId())
                .suggestedAlias(entity.getSuggestedAlias())
                .language(entity.getLanguage())
                .status(SuggestionStatus.valueOf(entity.getStatus()))
                .clientId(entity.getClientId())
                .reviewerId(entity.getReviewerId())
                .reviewComment(entity.getReviewComment())
                .build();

        suggestion.setCreatedAt(entity.getCreatedAt());
        suggestion.setUpdatedAt(entity.getUpdatedAt());
        suggestion.setVersion(entity.getVersion());

        return suggestion;
    }

    public static AliasSuggestionEntity toEntity(AliasSuggestion suggestion) {
        return AliasSuggestionEntity.builder()
                .id(suggestion.getId().getValue())
                .entityType(suggestion.getEntityType().name())
                .entityId(suggestion.getEntityId())
                .suggestedAlias(suggestion.getSuggestedAlias())
                .language(suggestion.getLanguage())
                .status(suggestion.getStatus().name())
                .clientId(suggestion.getClientId())
                .reviewerId(suggestion.getReviewerId())
                .reviewComment(suggestion.getReviewComment())
                .createdAt(suggestion.getCreatedAt())
                .updatedAt(suggestion.getUpdatedAt())
                .version(suggestion.getVersion())
                .build();
    }
}
