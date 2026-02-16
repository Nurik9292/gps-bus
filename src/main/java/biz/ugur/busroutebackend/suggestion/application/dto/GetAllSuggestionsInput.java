package biz.ugur.busroutebackend.suggestion.application.dto;

import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionStatus;
import lombok.Getter;

@Getter
public class GetAllSuggestionsInput {

    private final int page;
    private final int size;
    private final String sort;
    private final String order;
    private final SuggestionStatus status;
    private final SuggestionEntityType entityType;

    private GetAllSuggestionsInput(int page, int size, String sort, String order,
                                    SuggestionStatus status, SuggestionEntityType entityType) {
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.order = order;
        this.status = status;
        this.entityType = entityType;
    }

    public static GetAllSuggestionsInput fromParams(int page, int size, String sort, String order,
                                                     String status, String entityType) {
        SuggestionStatus parsedStatus = status != null && !status.isBlank()
                ? SuggestionStatus.valueOf(status.toUpperCase()) : null;
        SuggestionEntityType parsedEntityType = entityType != null && !entityType.isBlank()
                ? SuggestionEntityType.valueOf(entityType.toUpperCase()) : null;
        return new GetAllSuggestionsInput(page, size, sort, order, parsedStatus, parsedEntityType);
    }
}
