package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.application.dto.RouteSegmentDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.application.dto.TripSearchRequest;
import biz.ugur.busroutebackend.routing.application.dto.TripSearchResponse;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.repository.TripPlanRepository;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchTripsUseCase implements UseCase<TripSearchRequest, Mono<TripSearchResponse>> {

    private final FindDirectRoutesUseCase findDirectRoutesUseCase;
    private final FindRoutesWithTransfersUseCase findRoutesWithTransfersUseCase;
    private final TripPlanRepository tripPlanRepository;

    public SearchTripsUseCase(FindDirectRoutesUseCase findDirectRoutesUseCase,
                              FindRoutesWithTransfersUseCase findRoutesWithTransfersUseCase,
                              TripPlanRepository tripPlanRepository) {
        this.findDirectRoutesUseCase = findDirectRoutesUseCase;
        this.findRoutesWithTransfersUseCase = findRoutesWithTransfersUseCase;
        this.tripPlanRepository = tripPlanRepository;
    }

    @Override
    public Mono<TripSearchResponse> execute(TripSearchRequest request) {
        log.info("Trip search request: from ({},{}) to ({},{})",
                request.getFrom().getLatitude(), request.getFrom().getLongitude(),
                request.getTo().getLatitude(), request.getTo().getLongitude());

        // Конвертируем DTO в domain objects
        Location fromLocation = new Location(
                request.getFrom().getLatitude(),
                request.getFrom().getLongitude(),
                request.getFrom().getDescription() != null ? request.getFrom().getDescription() : "Starting point"
        );

        Location toLocation = new Location(
                request.getTo().getLatitude(),
                request.getTo().getLongitude(),
                request.getTo().getDescription() != null ? request.getTo().getDescription() : "Destination"
        );

        // STRATEGY: Сначала ищем прямые маршруты, потом добавляем с пересадками если нужно
        return findDirectRoutesUseCase.execute(new FindDirectRoutesUseCase.Command(fromLocation, toLocation))
                .flatMap(directPlan -> {
                    // Если найдено достаточно прямых маршрутов (3+), возвращаем их
                    if (directPlan.getTripOptions().size() >= 3) {
                        log.info("Found {} direct routes, sufficient options available", directPlan.getTripOptions().size());
                        return savePlanAndCreateResponse(directPlan, "Found direct routes");
                    }

                    // Иначе ищем дополнительно маршруты с пересадками
                    log.info("Found only {} direct routes, searching for transfer options", directPlan.getTripOptions().size());

                    return findRoutesWithTransfersUseCase.execute(
                            new FindRoutesWithTransfersUseCase.Command(fromLocation, toLocation)
                    ).flatMap(transferPlan -> {
                        // Объединяем прямые и transfer маршруты
                        transferPlan.getTripOptions().forEach(directPlan::addTripOption);

                        String message = String.format("Found %d route options (%d direct, %d with transfers)",
                                directPlan.getTripOptions().size(),
                                directPlan.getTripOptions().size(),
                                transferPlan.getTripOptions().size());

                        return savePlanAndCreateResponse(directPlan, message);
                    });
                })
                .doOnError(error -> log.error("Error searching for trips", error))
                .onErrorReturn(new TripSearchResponse("error", "Failed to find routes", List.of()));
    }

    // Сохранить план и создать ответ
    private Mono<TripSearchResponse> savePlanAndCreateResponse(TripPlan tripPlan, String message) {
        return tripPlanRepository.save(tripPlan)
                .then(Mono.fromCallable(() -> {
                    // Берем лучшие 5 вариантов
                    List<TripOptionDTO> options = tripPlan.getBestOptions(5)
                            .stream()
                            .map(this::convertToDTO)
                            .collect(Collectors.toList());

                    return new TripSearchResponse("success", message, options);
                }))
                .doOnSuccess(response -> log.info("Trip search completed: {} options returned",
                        response.getTripOptions().size()));
    }

    // Конвертировать domain object в DTO
    private TripOptionDTO convertToDTO(TripOption tripOption) {
        List<RouteSegmentDTO> segments = tripOption.getRouteSegments()
                .stream()
                .map(this::convertSegmentToDTO)
                .collect(Collectors.toList());

        return new TripOptionDTO(
                tripOption.getOptionId(),
                tripOption.getTripType().name().toLowerCase(),
                tripOption.getSummary(),
                tripOption.getTotalTravelMinutes(),
                tripOption.getTotalWalkingMinutes(),
                tripOption.getTransfersCount(),
                segments
        );
    }

    // Конвертировать segment в DTO
    private RouteSegmentDTO convertSegmentToDTO(RouteSegment segment) {
        RouteSegmentDTO dto = new RouteSegmentDTO(
                segment.getType().name().toLowerCase(),
                segment.getDetailedDescription(),
                segment.getDurationMinutes(),
                segment.getRouteNumber(),
                segment.getInstruction()
        );

        dto.setFromLocation(new RouteSegmentDTO.LocationPointDTO(
                segment.getFromLocation().getLatitude(),
                segment.getFromLocation().getLongitude(),
                segment.getFromLocation().getDescription()
        ));

        dto.setToLocation(new RouteSegmentDTO.LocationPointDTO(
                segment.getToLocation().getLatitude(),
                segment.getToLocation().getLongitude(),
                segment.getToLocation().getDescription()
        ));

        return dto;
    }
}