package biz.ugur.busroutebackend.suggestion.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class AliasSuggestionId extends ValueObject {

    private final String value;

    public AliasSuggestionId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("AliasSuggestion ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static AliasSuggestionId generate() {
        return new AliasSuggestionId(UUID.randomUUID().toString());
    }

    public static AliasSuggestionId of(String value) {
        return new AliasSuggestionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
