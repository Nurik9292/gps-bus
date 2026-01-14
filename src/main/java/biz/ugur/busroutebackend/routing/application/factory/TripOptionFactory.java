package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


@Component
public class TripOptionFactory {

    public TripOption createDirectOption(List<RouteSegment> segments,
                                         int initialWaitingMinutes,
                                         LocalDateTime departureTime) {
        return new TripOption(TripType.DIRECT, segments, initialWaitingMinutes, departureTime);
    }

    public TripOption createOneTransferOption(List<RouteSegment> segments,
                                              int initialWaitingMinutes,
                                              LocalDateTime departureTime) {
        return new TripOption(TripType.ONE_TRANSFER, segments, initialWaitingMinutes, departureTime);
    }

    public TripOption createTwoTransferOption(List<RouteSegment> segments,
                                              int initialWaitingMinutes,
                                              LocalDateTime departureTime) {
        return new TripOption(TripType.TWO_TRANSFER, segments, initialWaitingMinutes, departureTime);
    }

    public TripOption createWalkingOption(List<RouteSegment> segments, LocalDateTime departureTime) {
        return new TripOption(TripType.WALKING, segments, 0, departureTime);
    }
}
