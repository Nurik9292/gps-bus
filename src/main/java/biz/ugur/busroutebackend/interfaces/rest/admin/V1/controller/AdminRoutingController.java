package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTimetableGenerator;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTransferGenerator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ROUTING;

@RestController
@RequestMapping(V1_ADMIN_ROUTING)
public class AdminRoutingController {

    private final RaptorTimetableGenerator timetableGenerator;
    private final RaptorTransferGenerator transferGenerator;

    public AdminRoutingController(RaptorTimetableGenerator timetableGenerator,
                                   RaptorTransferGenerator transferGenerator) {
        this.timetableGenerator = timetableGenerator;
        this.transferGenerator = transferGenerator;
    }

    @PostMapping(path = "/regenerate-timetable", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RegenerationReport> regenerateTimetable() {
        return Mono.zip(
                timetableGenerator.regenerate(),
                transferGenerator.regenerate()
        ).map(tuple -> new RegenerationReport(
                tuple.getT1().tripsCreated(),
                tuple.getT1().stopTimesCreated(),
                tuple.getT1().skippedGroups(),
                tuple.getT2().rowsInserted(),
                tuple.getT2().pairsFound()
        ));
    }

    public record RegenerationReport(int trips,
                                      int stopTimes,
                                      int skippedRouteGroups,
                                      int stopTransfers,
                                      int stopPairsFound) {
    }
}
