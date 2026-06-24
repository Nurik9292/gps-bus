package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.interfaces.rest.routing.V2.response.TripOptionV2DTO;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionComparator;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseBuilderV2Test {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime T = LocalDateTime.of(2026, 5, 1, 10, 0);

    private final TripOptionDTOConverter converter = mock(TripOptionDTOConverter.class);
    private final ResponseBuilderV2 responseBuilder = new ResponseBuilderV2(converter);

    private TripOptionDTO dto(TripOption option) {
        return new TripOptionDTO(option.getOptionId(), option.getTripType().name().toLowerCase(),
                option.getSummary(), option.getTotalTravelMinutes(), option.getTotalWalkingMinutes(),
                option.getTransfersCount(), List.of());
    }

    @Test
    void exposesInitialWaitingSoTotalReconcilesWithSegments() {
        when(converter.convertToDTO(any())).thenAnswer(inv -> Mono.just(dto(inv.getArgument(0))));

        RouteSegment ride = RouteSegment.busRideSegment(A, B, 20, "29A");
        TripOption direct = new TripOption(TripType.DIRECT, List.of(ride), 15, T);

        TripSearchCriteria criteria = TripSearchCriteria.defaultCriteria();
        TripPlan plan = TripPlan.create(A, B, criteria);
        plan.addTripOption(direct, new TripOptionComparator(criteria));
        SearchContext context = SearchContext.of(A, B, criteria);

        StepVerifier.create(responseBuilder.createSuccessResponse(plan, context))
                .assertNext(response -> {
                    TripOptionV2DTO option = response.getTripOptions().getFirst();
                    int segmentsSum = direct.getRouteSegments().stream()
                            .mapToInt(RouteSegment::getDurationMinutes).sum();
                    assertThat(option.getInitialWaitingMinutes()).isEqualTo(15);
                    assertThat(option.getOption().getTotalTravelMinutes())
                            .isEqualTo(segmentsSum + option.getInitialWaitingMinutes());
                })
                .verifyComplete();
    }
}
