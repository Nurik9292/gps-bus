package biz.ugur.busroutebackend.routing.application.dto;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.Getter;

import java.util.List;

@Getter
public class SearchResult {

    private final String searchType;

    private final boolean successful;

    private final List<TripOption> options;

    private final String errorMessage;

    public static SearchResult successful(String searchType, List<TripOption> options) {
        return new SearchResult(searchType, true, options, null);
    }

    public static SearchResult failed(String searchType, String errorMessage) {
        return new SearchResult(searchType, false, List.of(), errorMessage);
    }

    private SearchResult(String searchType, boolean successful, List<TripOption> options, String errorMessage) {
        this.searchType = searchType;
        this.successful = successful;
        this.options = options;
        this.errorMessage = errorMessage;
    }

    public int getOptionsCount() { return options.size(); }
}