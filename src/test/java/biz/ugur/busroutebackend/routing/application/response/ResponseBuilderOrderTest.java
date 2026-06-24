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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResponseBuilderOrderTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);

    private final TripOptionDTOConverter converter = mock(TripOptionDTOConverter.class);
    private final ResponseBuilder responseBuilder = new ResponseBuilder(converter);

    private TripOption directOption(int busMinutes) {
        RouteSegment ride = RouteSegment.busRideSegment(A, B, busMinutes, "29A");
        return new TripOption(TripType.DIRECT, List.of(ride), 0, LocalDateTime.of(2026, 5, 1, 10, 0));
    }

    private TripOptionDTO dto(TripOption option) {
        return new TripOptionDTO(option.getOptionId(), "direct", option.getSummary(),
                option.getTotalTravelMinutes(), option.getTotalWalkingMinutes(),
                option.getTransfersCount(), List.of());
    }

    @Test
    void preservesComparatorOrderEvenWhenFasterOptionConvertsSlower() {
        TripOption fast = directOption(35);
        TripOption mid = directOption(36);
        TripOption slow = directOption(52);

        when(converter.convertToDTO(fast)).thenReturn(Mono.just(dto(fast)).delayElement(Duration.ofMillis(150)));
        when(converter.convertToDTO(mid)).thenReturn(Mono.just(dto(mid)).delayElement(Duration.ofMillis(10)));
        when(converter.convertToDTO(slow)).thenReturn(Mono.just(dto(slow)).delayElement(Duration.ofMillis(10)));

        TripSearchCriteria criteria = TripSearchCriteria.defaultCriteria();
        TripPlan plan = TripPlan.create(A, B, criteria);
        TripOptionComparator comparator = new TripOptionComparator(criteria);
        plan.addTripOption(slow, comparator);
        plan.addTripOption(fast, comparator);
        plan.addTripOption(mid, comparator);

        SearchContext context = SearchContext.of(A, B, criteria);

        StepVerifier.create(responseBuilder.createSuccessResponse(plan, context))
                .assertNext(response -> assertThat(response.getTripOptions())
                        .extracting(TripOptionDTO::getTotalTravelMinutes)
                        .containsExactly(35, 36, 52))
                .verifyComplete();
    }
}
