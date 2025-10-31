package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.service.TripValidationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class TripPlanFactory {

    private final TripValidationService tripValidationService;

    public TripPlan createNew(Coordinates origin, Coordinates destination, TripSearchCriteria criteria) {
        tripValidationService.validateTripLocations(origin, destination);

        return TripPlan.create(
                origin,
                destination,
                criteria != null ? criteria : TripSearchCriteria.defaultCriteria()
        );
    }

    public TripPlan createWithId(TripPlanId id, Coordinates origin, Coordinates destination, TripSearchCriteria criteria) {
        tripValidationService.validateTripLocations(origin, destination);

        return TripPlan.createWithId(
                id,
                origin,
                destination,
                criteria != null ? criteria : TripSearchCriteria.defaultCriteria()
        );
    }
}
