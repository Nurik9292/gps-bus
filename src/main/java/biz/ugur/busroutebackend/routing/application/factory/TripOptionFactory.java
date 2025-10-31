package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class TripOptionFactory {

    public TripOption createDirectOption(List<RouteSegment> segments) {
        return new TripOption(TripType.DIRECT, segments);
    }

    public TripOption createOneTransferOption(List<RouteSegment> segments) {
        return new TripOption(TripType.ONE_TRANSFER, segments);
    }

    public TripOption createTwoTransferOption(List<RouteSegment> segments) {
        return new TripOption(TripType.TWO_TRANSFER, segments);
    }

    public TripOption createWalkingOption(List<RouteSegment> segments) {
        return new TripOption(TripType.WALKING, segments);
    }
}
