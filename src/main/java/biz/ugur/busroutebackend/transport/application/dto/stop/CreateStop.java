package biz.ugur.busroutebackend.transport.application.dto.stop;

import java.math.BigDecimal;

public record CreateStop(
        String stopName,
        String nameEn,
        String nameTm,
        String stopCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isMajorStop,
        Boolean isActive) {
}
