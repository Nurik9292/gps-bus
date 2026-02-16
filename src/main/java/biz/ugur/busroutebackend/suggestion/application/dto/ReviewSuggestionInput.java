package biz.ugur.busroutebackend.suggestion.application.dto;

public record ReviewSuggestionInput(
        String suggestionId,
        String reviewerId,
        String comment
) {}
