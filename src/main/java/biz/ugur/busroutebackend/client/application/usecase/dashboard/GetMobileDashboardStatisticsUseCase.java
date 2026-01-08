package biz.ugur.busroutebackend.client.application.usecase.dashboard;

import biz.ugur.busroutebackend.client.application.dto.dashboard.MobileDashboardStatistics;
import biz.ugur.busroutebackend.client.application.dto.dashboard.MobileDashboardStatisticsResult;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class GetMobileDashboardStatisticsUseCase {

    private final ClientRepository clientRepository;
    private final VehicleRepository vehicleRepository;

    public Mono<MobileDashboardStatisticsResult> execute() {
        log.debug("Fetching mobile dashboard statistics");
        return fetchStatistics();
    }

    private Mono<MobileDashboardStatisticsResult> fetchStatistics() {
        return Mono.zip(
                clientRepository.countActiveClients().onErrorReturn(0L),
                vehicleRepository.findVehiclesInMotion().count().onErrorReturn(0L)
        ).map(tuple -> {
            MobileDashboardStatistics statistics = new MobileDashboardStatistics(
                    tuple.getT1(),
                    tuple.getT2()
            );
            log.info("Mobile dashboard statistics: activeClients={}, vehiclesInMotion={}",
                    statistics.activeClients(), statistics.activeVehicles());
            return MobileDashboardStatisticsResult.fromStatistics(statistics);
        }).doOnError(error -> log.error("Error fetching mobile dashboard statistics", error));
    }
}
