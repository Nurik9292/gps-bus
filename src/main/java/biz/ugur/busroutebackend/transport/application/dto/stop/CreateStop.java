package biz.ugur.busroutebackend.transport.application.dto.stop;

import java.math.BigDecimal;

public record CreateStop(
        String stopName,
        String nameEn,
        String nameTm,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isMajorStop,
        Boolean isActive,
        String cityId
) {
}
