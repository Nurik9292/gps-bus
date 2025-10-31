package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.BusStopEntity;
import org.springframework.stereotype.Component;


@Component
public class BusStopEntityMapper {

    public BusStopEntity toEntity(BusStop domain) {
        if (domain == null) {
            return null;
        }

        return BusStopEntity.builder()
                .id(domain.getId().getValue())
                .stopName(domain.getStopName())
                .nameEn(domain.getNameEn())
                .nameTm(domain.getNameTm())
                .cityId(domain.getCityId())
                .stopCode(domain.getStopCode() != null ? domain.getStopCode().getValue() : null)
                .latitude(domain.getLatitude())
                .longitude(domain.getLongitude())
                .isActive(domain.getIsActive())
                .isMajorStop(domain.getIsMajorStop())
                .servingRoutesCount(domain.getServingRoutesCount())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }

    public BusStop toDomain(BusStopEntity entity) {
        if (entity == null) {
            return null;
        }

        return BusStop.restore(
                BusStopId.of(entity.getId()),
                entity.getStopName(),
                entity.getNameEn(),
                entity.getNameTm(),
                entity.getStopCode() != null ? StopCode.of(entity.getStopCode()) : null,
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getIsActive(),
                entity.getIsMajorStop(),
                entity.getCityId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
