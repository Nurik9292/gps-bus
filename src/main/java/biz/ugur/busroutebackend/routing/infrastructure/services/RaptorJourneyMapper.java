package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorJourney;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorLeg;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TransferRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class RaptorJourneyMapper {

    private static final double WALKING_SPEED_KMH = 5.0;

    public Optional<DirectRouteResult> toDirect(RaptorJourney journey, RaptorTimetable timetable) {
        List<RaptorLeg> busLegs = busLegsOf(journey);
        if (busLegs.size() != 1) {
            return Optional.empty();
        }
        RaptorLeg leg = busLegs.get(0);
        BusRoute route = timetable.busRouteOf(leg.routeId());
        BusStop boarding = timetable.busStopOf(leg.fromStop());
        BusStop alighting = timetable.busStopOf(leg.toStop());
        if (route == null || boarding == null || alighting == null) {
            log.debug("[RAPTOR_MAP] cannot resolve route/stops for direct: route={} from={} to={}",
                    leg.routeId(), leg.fromStop(), leg.toStop());
            return Optional.empty();
        }

        return Optional.of(new DirectRouteResult(
                route,
                boarding,
                alighting,
                Math.max(1, leg.durationSec() / 60),
                leadingWalkKm(journey),
                trailingWalkKm(journey),
                leg.direction().getValue()
        ));
    }

    public Optional<TransferRouteResult> toOneTransfer(RaptorJourney journey, RaptorTimetable timetable) {
        List<RaptorLeg> busLegs = busLegsOf(journey);
        if (busLegs.size() != 2) {
            return Optional.empty();
        }
        RaptorLeg first = busLegs.get(0);
        RaptorLeg second = busLegs.get(1);
        BusRoute firstRoute = timetable.busRouteOf(first.routeId());
        BusRoute secondRoute = timetable.busRouteOf(second.routeId());
        BusStop boarding = timetable.busStopOf(first.fromStop());
        BusStop transfer = timetable.busStopOf(first.toStop());
        BusStop secondBoard = timetable.busStopOf(second.fromStop());
        BusStop alighting = timetable.busStopOf(second.toStop());
        if (firstRoute == null || secondRoute == null
                || boarding == null || transfer == null || secondBoard == null || alighting == null) {
            return Optional.empty();
        }

        int transferWaitSec = Math.max(0, second.startSec() - first.endSec());
        return Optional.of(new TransferRouteResult(
                firstRoute,
                boarding,
                transfer,
                secondBoard,
                secondRoute,
                alighting,
                Math.max(1, first.durationSec() / 60),
                Math.max(1, transferWaitSec / 60),
                Math.max(1, second.durationSec() / 60),
                leadingWalkKm(journey),
                trailingWalkKm(journey),
                first.direction().getValue(),
                second.direction().getValue()
        ));
    }

    public Optional<TwoTransferRouteResult> toTwoTransfers(RaptorJourney journey, RaptorTimetable timetable) {
        List<RaptorLeg> busLegs = busLegsOf(journey);
        if (busLegs.size() != 3) {
            return Optional.empty();
        }
        RaptorLeg l1 = busLegs.get(0);
        RaptorLeg l2 = busLegs.get(1);
        RaptorLeg l3 = busLegs.get(2);
        BusRoute r1 = timetable.busRouteOf(l1.routeId());
        BusRoute r2 = timetable.busRouteOf(l2.routeId());
        BusRoute r3 = timetable.busRouteOf(l3.routeId());
        BusStop boarding = timetable.busStopOf(l1.fromStop());
        BusStop t1 = timetable.busStopOf(l1.toStop());
        BusStop secondBoard = timetable.busStopOf(l2.fromStop());
        BusStop t2 = timetable.busStopOf(l2.toStop());
        BusStop thirdBoard = timetable.busStopOf(l3.fromStop());
        BusStop alighting = timetable.busStopOf(l3.toStop());
        if (r1 == null || r2 == null || r3 == null
                || boarding == null || t1 == null || secondBoard == null
                || t2 == null || thirdBoard == null || alighting == null) {
            return Optional.empty();
        }

        int wait1Sec = Math.max(0, l2.startSec() - l1.endSec());
        int wait2Sec = Math.max(0, l3.startSec() - l2.endSec());

        return Optional.of(new TwoTransferRouteResult(
                r1, boarding, t1,
                secondBoard,
                r2, t2,
                thirdBoard,
                r3, alighting,
                Math.max(1, l1.durationSec() / 60),
                Math.max(1, wait1Sec / 60),
                Math.max(1, l2.durationSec() / 60),
                Math.max(1, wait2Sec / 60),
                Math.max(1, l3.durationSec() / 60),
                leadingWalkKm(journey),
                trailingWalkKm(journey),
                l1.direction().getValue(),
                l2.direction().getValue(),
                l3.direction().getValue()
        ));
    }

    private List<RaptorLeg> busLegsOf(RaptorJourney journey) {
        List<RaptorLeg> bus = new ArrayList<>();
        for (RaptorLeg leg : journey.legs()) {
            if (leg.type() == RaptorLeg.LegType.BUS) {
                bus.add(leg);
            }
        }
        return bus;
    }

    private double leadingWalkKm(RaptorJourney journey) {
        if (journey.legs().isEmpty()) return 0.0;
        RaptorLeg first = journey.legs().get(0);
        if (first.type() != RaptorLeg.LegType.WALK) return 0.0;
        return walkSecToKm(first.durationSec());
    }

    private double trailingWalkKm(RaptorJourney journey) {
        if (journey.legs().isEmpty()) return 0.0;
        RaptorLeg last = journey.legs().get(journey.legs().size() - 1);
        if (last.type() != RaptorLeg.LegType.WALK) return 0.0;
        return walkSecToKm(last.durationSec());
    }

    private double walkSecToKm(int seconds) {
        return seconds * WALKING_SPEED_KMH / 3600.0;
    }
}
