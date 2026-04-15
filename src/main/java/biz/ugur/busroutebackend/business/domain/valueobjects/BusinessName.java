package biz.ugur.busroutebackend.business.domain.valueobjects;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class BusinessName extends ValueObject {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 200;

    private final String value;

    private BusinessName(String value) {
        if (value == null) {
            throw new BusinessValidationException("name", "must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH) {
            throw new BusinessValidationException("name",
                    "must be at least " + MIN_LENGTH + " characters");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new BusinessValidationException("name",
                    "must be at most " + MAX_LENGTH + " characters");
        }
        this.value = trimmed;
    }

    public static BusinessName of(String value) {
        return new BusinessName(value);
    }
}
