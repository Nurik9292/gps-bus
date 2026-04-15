package biz.ugur.busroutebackend.business.domain.valueobjects;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class TaxNumber extends ValueObject {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 32;

    private final String value;

    private TaxNumber(String value) {
        if (value == null) {
            throw new BusinessValidationException("taxNumber", "must not be null");
        }
        String trimmed = value.trim().replaceAll("\\s+", "");
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new BusinessValidationException("taxNumber",
                    "length must be between " + MIN_LENGTH + " and " + MAX_LENGTH);
        }
        if (!trimmed.matches("[0-9A-Za-z\\-]+")) {
            throw new BusinessValidationException("taxNumber",
                    "only alphanumerics and dashes are allowed");
        }
        this.value = trimmed;
    }

    public static TaxNumber of(String value) {
        return new TaxNumber(value);
    }
}
