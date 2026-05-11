package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorJourney;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorLeg;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorRoute;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTransfer;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTrip;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class RaptorEngine {

    private static final int INF = Integer.MAX_VALUE;

    public List<RaptorJourney> findJourneys(RaptorTimetable timetable,
                                             BusStopId fromStop,
                                             BusStopId toStop,
                                             int departureTimeSec,
                                             int maxRounds) {
        if (fromStop.equals(toStop)) {
            return List.of();
        }

        List<Map<BusStopId, Integer>> tau = new ArrayList<>(maxRounds + 1);
        List<Map<BusStopId, JourneyStep>> parents = new ArrayList<>(maxRounds + 1);
        for (int k = 0; k <= maxRounds; k++) {
            tau.add(new HashMap<>());
            parents.add(new HashMap<>());
        }

        tau.get(0).put(fromStop, departureTimeSec);

        applyWalkingTransfers(timetable, fromStop, departureTimeSec, tau.get(0), parents.get(0));

        Set<BusStopId> marked = new HashSet<>();
        marked.add(fromStop);
        marked.addAll(tau.get(0).keySet());

        for (int k = 1; k <= maxRounds; k++) {
            seedRoundFromPrevious(tau.get(k - 1), tau.get(k));

            Map<RaptorRoute, RouteEntryPoint> queue = collectQueue(timetable, marked);
            Set<BusStopId> markedNext = new HashSet<>();
            scanRoutes(queue, tau.get(k - 1), tau.get(k), parents.get(k), markedNext);
            applyTransfersPhase(timetable, tau.get(k), parents.get(k), markedNext);

            if (markedNext.isEmpty()) {
                break;
            }
            marked = markedNext;

            Integer prev = tau.get(k - 1).get(toStop);
            Integer curr = tau.get(k).get(toStop);
            if (curr != null && curr.equals(prev)) {
                break;
            }
        }

        return collectJourneys(timetable, fromStop, toStop, departureTimeSec, tau, parents, maxRounds);
    }

    private void seedRoundFromPrevious(Map<BusStopId, Integer> prev, Map<BusStopId, Integer> curr) {
        for (Map.Entry<BusStopId, Integer> e : prev.entrySet()) {
            curr.put(e.getKey(), e.getValue());
        }
    }

    private void applyWalkingTransfers(RaptorTimetable timetable,
                                        BusStopId originStop,
                                        int originArrival,
                                        Map<BusStopId, Integer> tauRow,
                                        Map<BusStopId, JourneyStep> parentRow) {
        for (RaptorTransfer transfer : timetable.transfersFrom(originStop)) {
            int walkArrival = originArrival + transfer.walkingSeconds();
            int current = tauRow.getOrDefault(transfer.toStopId(), INF);
            if (walkArrival < current) {
                tauRow.put(transfer.toStopId(), walkArrival);
                parentRow.put(transfer.toStopId(),
                        JourneyStep.walk(originStop, originArrival, walkArrival));
            }
        }
    }

    private Map<RaptorRoute, RouteEntryPoint> collectQueue(RaptorTimetable timetable,
                                                            Set<BusStopId> marked) {
        Map<RaptorRoute, RouteEntryPoint> queue = new HashMap<>();
        for (BusStopId stop : marked) {
            for (RaptorRoute route : timetable.routesAt(stop)) {
                int seq = route.firstSequenceOf(stop);
                if (seq < 0) continue;
                RouteEntryPoint existing = queue.get(route);
                if (existing == null || seq < existing.sequence()) {
                    queue.put(route, new RouteEntryPoint(seq, stop));
                }
            }
        }
        return queue;
    }

    private void scanRoutes(Map<RaptorRoute, RouteEntryPoint> queue,
                             Map<BusStopId, Integer> tauPrev,
                             Map<BusStopId, Integer> tauCurr,
                             Map<BusStopId, JourneyStep> parentCurr,
                             Set<BusStopId> markedNext) {
        for (Map.Entry<RaptorRoute, RouteEntryPoint> entry : queue.entrySet()) {
            RaptorRoute route = entry.getKey();
            int startSeq = entry.getValue().sequence();

            RaptorTrip currentTrip = null;
            int boardSeq = -1;
            BusStopId boardStop = null;
            int boardTimeSec = -1;

            for (int seq = startSeq; seq <= route.lastSequenceIndex(); seq++) {
                BusStopId stop = route.stopAt(seq);

                if (currentTrip != null) {
                    int arrival = route.arrivalSecAt(currentTrip, seq);
                    int current = tauCurr.getOrDefault(stop, INF);
                    if (arrival < current) {
                        tauCurr.put(stop, arrival);
                        parentCurr.put(stop, JourneyStep.bus(
                                boardStop, boardTimeSec, arrival,
                                route, currentTrip, boardSeq, seq));
                        markedNext.add(stop);
                    }
                }

                int earliestBoarding = tauPrev.getOrDefault(stop, INF);
                if (earliestBoarding != INF) {
                    RaptorTrip trip = route.earliestTripAt(seq, earliestBoarding);
                    if (trip != null && (currentTrip == null || !trip.id().equals(currentTrip.id()))) {
                        int candidateBoardTime = route.departureSecAt(trip, seq);
                        if (currentTrip == null
                                || candidateBoardTime < route.departureSecAt(currentTrip, seq)) {
                            currentTrip = trip;
                            boardSeq = seq;
                            boardStop = stop;
                            boardTimeSec = candidateBoardTime;
                        }
                    }
                }
            }
        }
    }

    private void applyTransfersPhase(RaptorTimetable timetable,
                                      Map<BusStopId, Integer> tauCurr,
                                      Map<BusStopId, JourneyStep> parentCurr,
                                      Set<BusStopId> markedNext) {
        List<BusStopId> snapshot = new ArrayList<>(markedNext);
        for (BusStopId stop : snapshot) {
            int stopArrival = tauCurr.getOrDefault(stop, INF);
            if (stopArrival == INF) continue;
            for (RaptorTransfer transfer : timetable.transfersFrom(stop)) {
                int walkArrival = stopArrival + transfer.walkingSeconds();
                int current = tauCurr.getOrDefault(transfer.toStopId(), INF);
                if (walkArrival < current) {
                    tauCurr.put(transfer.toStopId(), walkArrival);
                    parentCurr.put(transfer.toStopId(),
                            JourneyStep.walk(stop, stopArrival, walkArrival));
                    markedNext.add(transfer.toStopId());
                }
            }
        }
    }

    private List<RaptorJourney> collectJourneys(RaptorTimetable timetable,
                                                 BusStopId fromStop,
                                                 BusStopId toStop,
                                                 int departureTimeSec,
                                                 List<Map<BusStopId, Integer>> tau,
                                                 List<Map<BusStopId, JourneyStep>> parents,
                                                 int maxRounds) {
        List<RaptorJourney> journeys = new ArrayList<>();
        int bestSoFar = INF;
        for (int k = 1; k <= maxRounds; k++) {
            Integer arrival = tau.get(k).get(toStop);
            if (arrival == null || arrival == INF) continue;
            if (arrival >= bestSoFar) continue;
            bestSoFar = arrival;
            RaptorJourney j = reconstruct(timetable, fromStop, toStop, departureTimeSec,
                    arrival, k, parents);
            if (j != null) {
                journeys.add(j);
            }
        }
        return journeys;
    }

    private RaptorJourney reconstruct(RaptorTimetable timetable,
                                       BusStopId fromStop,
                                       BusStopId toStop,
                                       int departureTimeSec,
                                       int arrivalTimeSec,
                                       int roundK,
                                       List<Map<BusStopId, JourneyStep>> parents) {
        List<RaptorLeg> legs = new ArrayList<>();
        BusStopId current = toStop;
        int round = roundK;

        while (round >= 0 && !current.equals(fromStop)) {
            JourneyStep step = parents.get(round).get(current);
            if (step == null) {
                if (round > 0) {
                    round--;
                    continue;
                }
                log.debug("[RAPTOR_ENGINE] cannot reconstruct: no parent for {} at round {}",
                        current, round);
                return null;
            }
            if (step.isWalk()) {
                legs.add(RaptorLeg.walk(step.fromStop(), current, step.boardTimeSec(), step.alightTimeSec()));
            } else {
                legs.add(RaptorLeg.bus(step.fromStop(), current,
                        step.boardTimeSec(), step.alightTimeSec(),
                        step.route().routeId(), step.route().direction()));
                round--;
            }
            current = step.fromStop();
        }

        if (!current.equals(fromStop)) {
            return null;
        }

        Collections.reverse(legs);
        int numTransfers = Math.max(0, (int) legs.stream()
                .filter(l -> l.type() == RaptorLeg.LegType.BUS).count() - 1);
        return new RaptorJourney(fromStop, toStop, departureTimeSec, arrivalTimeSec, numTransfers, legs);
    }

    private record RouteEntryPoint(int sequence, BusStopId boardingStop) {
    }

    private record JourneyStep(BusStopId fromStop,
                                int boardTimeSec,
                                int alightTimeSec,
                                RaptorRoute route,
                                RaptorTrip trip,
                                int boardSeq,
                                int alightSeq) {

        static JourneyStep walk(BusStopId fromStop, int boardTimeSec, int alightTimeSec) {
            return new JourneyStep(fromStop, boardTimeSec, alightTimeSec, null, null, -1, -1);
        }

        static JourneyStep bus(BusStopId fromStop,
                                int boardTimeSec,
                                int alightTimeSec,
                                RaptorRoute route,
                                RaptorTrip trip,
                                int boardSeq,
                                int alightSeq) {
            return new JourneyStep(fromStop, boardTimeSec, alightTimeSec, route, trip, boardSeq, alightSeq);
        }

        boolean isWalk() {
            return route == null;
        }
    }
}
