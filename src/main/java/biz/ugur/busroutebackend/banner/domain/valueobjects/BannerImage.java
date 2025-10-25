package biz.ugur.busroutebackend.banner.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper= false)
public class BannerImage extends ValueObject {

    private final String value;

    private BannerImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Banner image URL cannot be null or empty");
        }
       this.value = imageUrl.trim();
    }

    public static BannerImage of(String imageUrl) {
        return new BannerImage(imageUrl);
    }
}
