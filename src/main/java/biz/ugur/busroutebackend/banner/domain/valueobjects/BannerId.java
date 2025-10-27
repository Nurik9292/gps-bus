package biz.ugur.busroutebackend.banner.domain.valueobjects;

import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class BannerId extends ValueObject {

    private final String value;

    public BannerId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BannerValidationException("Banner ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static BannerId generate() {
        return new BannerId(UUID.randomUUID().toString());
    }

    public static BannerId of(String value) {
        return new BannerId(value);
    }

}