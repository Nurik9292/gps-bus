package biz.ugur.busroutebackend.routing.infrastructure.persistence.mapper;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.routing.infrastructure.persistence.entity.TripPlanEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;


@Component
@RequiredArgsConstructor
public class TripPlanMapper {

    public TripPlanEntity toEntity(TripPlan domain) {
        return TripPlanEntity.builder()
                .id(domain.getId().getValue())
                .originLatitude(domain.getOriginLocation().getLatitudeAsDouble())
                .originLongitude(domain.getOriginLocation().getLongitudeAsDouble())
                .destinationLatitude(domain.getDestinationLocation().getLatitudeAsDouble())
                .destinationLongitude(domain.getDestinationLocation().getLongitudeAsDouble())
                .searchTime(domain.getSearchTime())
                .optionsCount(domain.getTripOptionsCount())
                .maxTransfers(domain.getSearchCriteria().getMaxTransfers())
                .maxWalkingDistanceMeters(domain.getSearchCriteria().getMaxWalkingDistanceMeters())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }


    public TripPlan toDomain(TripPlanEntity entity) {
        Coordinates origin = Coordinates.of(entity.getOriginLatitude(), entity.getOriginLongitude());
        Coordinates destination = Coordinates.of(entity.getDestinationLatitude(), entity.getDestinationLongitude());

        TripSearchCriteria criteria = new TripSearchCriteria(
                entity.getMaxWalkingDistanceMeters() != null ? entity.getMaxWalkingDistanceMeters() : 1200,
                entity.getMaxTransfers() != null ? entity.getMaxTransfers() : 2,
                true,
                true
        );


        return TripPlan.restore(
                TripPlanId.of(entity.getId()),
                origin,
                destination,
                criteria,
                entity.getSearchTime(),
                entity.getOptionsCount() != null ? entity.getOptionsCount() : 0,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
