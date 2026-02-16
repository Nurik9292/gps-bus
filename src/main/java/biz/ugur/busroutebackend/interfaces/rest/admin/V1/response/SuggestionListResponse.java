package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class SuggestionListResponse {

    @JsonProperty("suggestions")
    private final List<SuggestionResponse> suggestions;

    @JsonProperty("active_count")
    private final Long activeCount;

    @JsonProperty("pagination")
    private final PaginationInfo pagination;

    public SuggestionListResponse(List<SuggestionResponse> suggestions, Long activeCount, PaginationInfo pagination) {
        this.suggestions = suggestions;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    public static SuggestionListResponse fromResult(SuggestionList list) {
        return new SuggestionListResponse(
                list.getSuggestions().stream().map(SuggestionResponse::fromResult).toList(),
                list.getActiveCount(),
                list.getPagination()
        );
    }
}
