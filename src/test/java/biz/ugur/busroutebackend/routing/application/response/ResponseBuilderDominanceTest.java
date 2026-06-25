package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
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

class ResponseBuilderDominanceTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime T = LocalDateTime.of(2026, 5, 1, 10, 0);

    private final TripOptionDTOConverter converter = mock(TripOptionDTOConverter.class);
    private final ResponseBuilder responseBuilder = new ResponseBuilder(converter);

    private TripOption directOption(int busMinutes) {
        RouteSegment ride = RouteSegment.busRideSegment(A, B, busMinutes, "29A");
        return new TripOption(TripType.DIRECT, List.of(ride), 0, T);
    }

    private TripOption oneTransferOption(int totalMinutes) {
        int ride1 = Math.max(1, totalMinutes / 3);
        int transferWait = 2;
        int ride2 = Math.max(1, totalMinutes - ride1 - transferWait);
        List<RouteSegment> segments = List.of(
                RouteSegment.busRideSegment(A, A, ride1, "1"),
                RouteSegment.transferSegment(A, transferWait),
                RouteSegment.busRideSegment(A, B, ride2, "2"));
        return new TripOption(TripType.ONE_TRANSFER, segments, 0, T);
    }

    private TripOption walkingOnlyOption(int walkMinutes) {
        RouteSegment walk = RouteSegment.walkingSegment(A, B, walkMinutes);
        return new TripOption(TripType.WALKING_ONLY, List.of(walk), 0, T);
    }

    private TripOptionDTO dto(TripOption option) {
        return new TripOptionDTO(option.getOptionId(), option.getTripType().name().toLowerCase(),
                option.getSummary(), option.getTotalTravelMinutes(), option.getTotalWalkingMinutes(),
                option.getTransfersCount(), List.of());
    }

    @Test
    void dropsDominatedTransfersButKeepsDirectAndWalking() {
        when(converter.convertToDTO(any())).thenAnswer(inv -> Mono.just(dto(inv.getArgument(0))));

        TripOption direct = directOption(35);
        TripOption walking = walkingOnlyOption(36);
        TripOption slowTransfer1 = oneTransferOption(52);
        TripOption slowTransfer2 = oneTransferOption(69);

        TripSearchCriteria criteria = TripSearchCriteria.defaultCriteria();
        TripPlan plan = TripPlan.create(A, B, criteria);
        TripOptionComparator comparator = new TripOptionComparator(criteria);
        plan.addTripOption(slowTransfer1, comparator);
        plan.addTripOption(direct, comparator);
        plan.addTripOption(slowTransfer2, comparator);
        plan.addTripOption(walking, comparator);

        SearchContext context = SearchContext.of(A, B, criteria);

        StepVerifier.create(responseBuilder.createSuccessResponse(plan, context))
                .assertNext(response -> assertThat(response.getTripOptions())
                        .extracting(TripOptionDTO::getTotalTravelMinutes)
                        .containsExactly(35, 36))
                .verifyComplete();
    }
}
