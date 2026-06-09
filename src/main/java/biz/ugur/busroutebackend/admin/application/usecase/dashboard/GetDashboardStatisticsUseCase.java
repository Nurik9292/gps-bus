package biz.ugur.busroutebackend.admin.application.usecase.dashboard;

import biz.ugur.busroutebackend.admin.application.dto.dashboard.DashboardStatistics;
import biz.ugur.busroutebackend.admin.application.dto.dashboard.DashboardStatisticsResult;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
@RequiredArgsConstructor
public class GetDashboardStatisticsUseCase {

    private final BusRouteRepository routeRepository;
    private final BusStopRepository stopRepository;
    private final VehicleRepository vehicleRepository;
    private final AdminRepository adminRepository;
    private final CityRepository cityRepository;

    public Mono<DashboardStatisticsResult> execute() {
        log.debug("Fetching dashboard statistics");
        return fetchAllStatistics();
    }

    private Mono<DashboardStatisticsResult> fetchAllStatistics() {
        Mono<DashboardStatistics.RouteStats> routeStats = Mono.zip(
                countOrZero(routeRepository.count(), "routes.total"),
                countOrZero(routeRepository.countActiveRoutes(), "routes.active")
        ).map(tuple -> new DashboardStatistics.RouteStats(tuple.getT1(), tuple.getT2()))
                .onErrorReturn(DashboardStatistics.RouteStats.empty());

        Mono<DashboardStatistics.StopStats> stopStats = Mono.zip(
                countOrZero(stopRepository.count(), "stops.total"),
                countOrZero(stopRepository.countActiveStops(), "stops.active")
        ).map(tuple -> new DashboardStatistics.StopStats(tuple.getT1(), tuple.getT2()))
                .onErrorReturn(DashboardStatistics.StopStats.empty());

        Mono<DashboardStatistics.VehicleStats> vehicleStats = Mono.zip(
                countOrZero(vehicleRepository.count(), "vehicles.total"),
                countOrZero(vehicleRepository.countActiveVehicles(), "vehicles.active"),
                countOrZero(vehicleRepository.findVehiclesInMotion().count(), "vehicles.inMotion")
        ).map(tuple -> new DashboardStatistics.VehicleStats(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .onErrorReturn(DashboardStatistics.VehicleStats.empty());

        Mono<DashboardStatistics.AdminStats> adminStats = Mono.zip(
                countOrZero(adminRepository.count(), "admins.total"),
                countOrZero(adminRepository.countActiveAdmins(), "admins.active")
        ).map(tuple -> new DashboardStatistics.AdminStats(tuple.getT1(), tuple.getT2()))
                .onErrorReturn(DashboardStatistics.AdminStats.empty());

        Mono<DashboardStatistics.CityStats> cityStats = Mono.zip(
                countOrZero(cityRepository.count(), "cities.total"),
                countOrZero(cityRepository.countActiveCities(), "cities.active")
        ).map(tuple -> new DashboardStatistics.CityStats(tuple.getT1(), tuple.getT2()))
                .onErrorReturn(DashboardStatistics.CityStats.empty());

        return Mono.zip(routeStats, stopStats, vehicleStats, adminStats, cityStats)
                .map(tuple -> {
                    DashboardStatistics statistics = new DashboardStatistics(
                            tuple.getT1(),
                            tuple.getT2(),
                            tuple.getT3(),
                            tuple.getT4(),
                            tuple.getT5()
                    );
                    log.info("Dashboard statistics: routes={}/{}, stops={}/{}, vehicles={}/{} (moving: {}), admins={}/{}, cities={}/{}",
                            statistics.routes().active(), statistics.routes().total(),
                            statistics.stops().active(), statistics.stops().total(),
                            statistics.vehicles().active(), statistics.vehicles().total(), statistics.vehicles().inMotion(),
                            statistics.admins().active(), statistics.admins().total(),
                            statistics.cities().active(), statistics.cities().total());
                    return DashboardStatisticsResult.fromStatistics(statistics);
                })
                .doOnError(error -> log.error("Error fetching dashboard statistics", error));
    }

    private Mono<Long> countOrZero(Mono<Long> source, String label) {
        return source.doOnError(e -> log.error("Dashboard count '{}' failed, defaulting to 0", label, e))
                .onErrorReturn(0L);
    }
}
