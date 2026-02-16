package biz.ugur.busroutebackend.suggestion.domain.exceptions;

public class SuggestionNotFoundException extends SuggestionDomainException {

    public SuggestionNotFoundException(String suggestionId) {
        super("suggestion.not_found", "Suggestion not found with ID: " + suggestionId);
    }
}
