package biz.ugur.busroutebackend.banner.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;


@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class BannerTitle extends ValueObject {

    private final String value;

    private BannerTitle(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Banner title cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static BannerTitle of(String value) {
        return new BannerTitle(value);
    }

}
