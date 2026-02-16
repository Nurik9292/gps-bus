package biz.ugur.busroutebackend.suggestion.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@ToString
@Getter
@EqualsAndHashCode
public final class SuggestionList implements PagedList<SuggestionResult> {

    private final List<SuggestionResult> suggestions;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public SuggestionList(List<SuggestionResult> suggestions, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.suggestions = Collections.unmodifiableList(suggestions);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    @Override
    public List<SuggestionResult> items() {
        return suggestions;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }
}
