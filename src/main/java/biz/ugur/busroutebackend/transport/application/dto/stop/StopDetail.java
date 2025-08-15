package biz.ugur.busroutebackend.transport.application.dto.stop;

import java.math.BigDecimal;

public record StopDetail(
        String id,
        String stopName,
        String nameEn,
        String nameTm,
        String stopCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isActive,
        Boolean isMajorStop,
        Integer servingRoutesCount,
        String cityId
) {}
