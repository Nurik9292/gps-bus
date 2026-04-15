package biz.ugur.busroutebackend.business.domain.valueobjects;

import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class BusinessId extends ValueObject {

    private final String value;

    private BusinessId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessValidationException("id", "must not be null or blank");
        }
        this.value = value.trim();
    }

    public static BusinessId generate() {
        return new BusinessId(UUID.randomUUID().toString());
    }

    public static BusinessId of(String value) {
        return new BusinessId(value);
    }
}
