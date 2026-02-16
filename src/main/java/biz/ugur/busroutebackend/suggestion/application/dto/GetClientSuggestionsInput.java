package biz.ugur.busroutebackend.suggestion.application.dto;

import lombok.Getter;

@Getter
public class GetClientSuggestionsInput {

    private final String clientId;
    private final int page;
    private final int size;
    private final String sort;
    private final String order;

    private GetClientSuggestionsInput(String clientId, int page, int size, String sort, String order) {
        this.clientId = clientId;
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.order = order;
    }

    public static GetClientSuggestionsInput fromParams(String clientId, int page, int size, String sort, String order) {
        return new GetClientSuggestionsInput(clientId, page, size, sort, order);
    }
}
