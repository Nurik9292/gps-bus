package biz.ugur.busroutebackend.transport.application.usecase.vehicle;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import biz.ugur.busroutebackend.transport.application.dto.VehicleData;
import biz.ugur.busroutebackend.transport.application.dto.VehicleListResult;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.specification.VehicleSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetAllVehiclesWithPaginationUseCase implements UseCase<Mono<GetAllVehiclesWithPaginationUseCase.Query>, Mono<VehicleListResult>> {

    private final VehicleRepository vehicleRepository;

    public record Query(
            int page,
            int size,
            String sort,
            String order,
            Boolean active,
            String search
    ) {
        public static Query fromParams(int page, int size, String sort, String order, Boolean active, String search) {
            return new Query(page, size, sort, order, active, search);
        }
    }

    @Override
    public Mono<VehicleListResult> execute(Mono<Query> queryMono) {
        return queryMono.flatMap(query -> {
            log.debug("Getting vehicles with pagination: page={}, size={}, sort={}, order={}, active={}, search={}",
                    query.page(), query.size(), query.sort(), query.order(), query.active(), query.search());

            VehicleSpecification specification = buildSpecification(query);
            Pageable pageable = buildPageable(query);

            return Mono.zip(
                    vehicleRepository.findBySpecification(specification, pageable)
                            .map(VehicleData::fromDomain)
                            .collectList(),
                    vehicleRepository.countBySpecification(specification),
                    vehicleRepository.countActiveVehicles()
            ).map(tuple -> {
                List<VehicleData> vehicles = tuple.getT1();
                Long totalCount = tuple.getT2();
                Long activeCount = tuple.getT3();

                PaginationInfo pagination = PaginationInfo.of(
                        query.page(),
                        query.size(),
                        totalCount
                );

                return VehicleListResult.of(vehicles, pagination, totalCount, activeCount);
            });
        });
    }

    private VehicleSpecification buildSpecification(Query query) {
        VehicleSpecification.Builder builder = VehicleSpecification.builder();

        if (query.active() != null) {
            builder.isActive(query.active());
        }

        if (query.search() != null && !query.search().trim().isEmpty()) {
            builder.licensePlateContains(query.search().trim());
        }

        return builder.build();
    }

    private Pageable buildPageable(Query query) {
        String sortField = query.sort() != null ? query.sort() : "created_at";
        Sort.Direction direction = "asc".equalsIgnoreCase(query.order())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(
                query.page() - 1,
                query.size(),
                Sort.by(direction, sortField)
        );
    }
}
