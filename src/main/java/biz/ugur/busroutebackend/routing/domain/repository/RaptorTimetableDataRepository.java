package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.List;

public interface RaptorTimetableDataRepository {

    Mono<RawTimetableData> loadAll();

    record RawTimetableData(List<TripRow> trips,
                             List<StopTimeRow> stopTimes,
                             List<TransferRow> transfers) {
    }

    record TripRow(String tripId,
                    String routeId,
                    int direction,
                    String serviceId,
                    Integer headwaySeconds,
                    LocalTime startTime,
                    LocalTime endTime) {
    }

    record StopTimeRow(String tripId,
                        int stopSequence,
                        String stopId,
                        int arrivalOffsetSec,
                        int departureOffsetSec) {
    }

    record TransferRow(String fromStopId,
                        String toStopId,
                        int walkingSeconds,
                        int distanceMeters) {
    }
}
