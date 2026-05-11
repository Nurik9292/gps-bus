package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.repository.StopTransferRepository;
import biz.ugur.busroutebackend.routing.domain.repository.TripRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(
        prefix = "routing.raptor.timetable-generation",
        name = "enabled",
        havingValue = "true")
@Slf4j
public class RaptorTimetableBootstrapper {

    private final TripRepository tripRepository;
    private final StopTransferRepository stopTransferRepository;
    private final RaptorTimetableGenerator timetableGenerator;
    private final RaptorTransferGenerator transferGenerator;

    public RaptorTimetableBootstrapper(TripRepository tripRepository,
                                        StopTransferRepository stopTransferRepository,
                                        RaptorTimetableGenerator timetableGenerator,
                                        RaptorTransferGenerator transferGenerator) {
        this.tripRepository = tripRepository;
        this.stopTransferRepository = stopTransferRepository;
        this.timetableGenerator = timetableGenerator;
        this.transferGenerator = transferGenerator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generateOnFirstStart() {
        Mono.zip(tripRepository.count(), stopTransferRepository.count())
                .flatMap(counts -> {
                    long trips = counts.getT1();
                    long transfers = counts.getT2();
                    if (trips > 0 && transfers > 0) {
                        log.info("[RAPTOR_GEN] skip auto-bootstrap: trips={} transfers={} already populated",
                                trips, transfers);
                        return Mono.empty();
                    }
                    log.info("[RAPTOR_GEN] auto-bootstrap on startup (trips={} transfers={})",
                            trips, transfers);
                    return Mono.zip(timetableGenerator.regenerate(), transferGenerator.regenerate())
                            .doOnNext(t -> log.info(
                                    "[RAPTOR_GEN] auto-bootstrap done: trips={} stop_times={} stop_transfers={}",
                                    t.getT1().tripsCreated(), t.getT1().stopTimesCreated(),
                                    t.getT2().rowsInserted()))
                            .then();
                })
                .doOnError(err -> log.error("[RAPTOR_GEN] auto-bootstrap failed", err))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
