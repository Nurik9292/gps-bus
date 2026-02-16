package biz.ugur.busroutebackend.suggestion.application.dto;

import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;

public record CreateSuggestionInput(
        SuggestionEntityType entityType,
        String entityId,
        String suggestedAlias,
        String language,
        String clientId
) {}
