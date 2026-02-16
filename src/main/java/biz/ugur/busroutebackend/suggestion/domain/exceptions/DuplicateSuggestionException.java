package biz.ugur.busroutebackend.suggestion.domain.exceptions;

public class DuplicateSuggestionException extends SuggestionDomainException {

    public DuplicateSuggestionException(String alias) {
        super("suggestion.duplicate", "Suggestion with alias already exists: " + alias);
    }
}
