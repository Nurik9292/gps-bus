package biz.ugur.busroutebackend.routing.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.events.TripOptionsCalculatedEvent;
import biz.ugur.busroutebackend.routing.domain.events.TripPlanCreatedEvent;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionComparator;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripPlanTest {

    private static final Coordinates ORIGIN = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates HUB = Coordinates.of(37.9651, 58.3311);
    private static final Coordinates DEST = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime DEPART = LocalDateTime.of(2026, 5, 1, 10, 0);

    private TripPlan plan;
    private TripOptionComparator comparator;

    @BeforeEach
    void setUp() {
        plan = TripPlan.create(ORIGIN, DEST, TripSearchCriteria.defaultCriteria());
        comparator = new TripOptionComparator(TripSearchCriteria.defaultCriteria());
    }

    private TripOption direct(int minutes) {
        return new TripOption(TripType.DIRECT,
                List.of(RouteSegment.busRideSegment(ORIGIN, DEST, minutes, "29A")),
                0, DEPART);
    }

    private TripOption oneTransfer(int minutesEach) {
        return new TripOption(TripType.ONE_TRANSFER,
                List.of(
                        RouteSegment.busRideSegment(ORIGIN, HUB, minutesEach, "1"),
                        RouteSegment.transferSegment(HUB, 2),
                        RouteSegment.busRideSegment(HUB, DEST, minutesEach, "2")),
                0, DEPART);
    }

    @Nested
    class Create {

        @Test
        void createsEmptyPlanAndEmitsCreatedEvent() {
            assertNotNull(plan.getId());
            assertEquals(0, plan.getTripOptionsCount());
            assertEquals(1, plan.getDomainEvents().size());
            assertInstanceOf(TripPlanCreatedEvent.class, plan.getDomainEvents().get(0));
        }

        @Test
        void defaultCriteriaUsedWhenNull() {
            TripPlan p = TripPlan.create(ORIGIN, DEST, null);
            assertNotNull(p.getSearchCriteria());
            assertEquals(TripSearchCriteria.defaultCriteria().getMaxTransfers(),
                    p.getSearchCriteria().getMaxTransfers());
        }

        @Test
        void createWithIdRespectsGivenId() {
            TripPlanId id = TripPlanId.generate();
            TripPlan p = TripPlan.createWithId(id, ORIGIN, DEST, TripSearchCriteria.defaultCriteria());
            assertEquals(id, p.getId());
            assertEquals(1, p.getDomainEvents().size());
        }
    }

    @Nested
    class AddAndSortOptions {

        @Test
        void addTripOptionEmitsCalculatedEvent() {
            plan.addTripOption(direct(15), comparator);
            assertEquals(1, plan.getTripOptionsCount());
            assertTrue(plan.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof TripOptionsCalculatedEvent));
        }

        @Test
        void addTripOptionRejectsNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> plan.addTripOption(null, comparator));
        }

        @Test
        void addTripOptionRespectsMaxCapacityAndEvictsWorst() {
            for (int i = 0; i < 10; i++) {
                plan.addTripOption(direct(20 + i), comparator);
            }
            assertEquals(10, plan.getTripOptionsCount());

            TripOption better = direct(5);
            plan.addTripOption(better, comparator);
            assertEquals(10, plan.getTripOptionsCount());
            assertTrue(plan.getTripOptions().contains(better));
        }

        @Test
        void addTripOptionKeepsTopWhenNewIsWorse() {
            for (int i = 0; i < 10; i++) {
                plan.addTripOption(direct(10 + i), comparator);
            }
            TripOption worse = direct(50);
            plan.addTripOption(worse, comparator);
            assertFalse(plan.getTripOptions().contains(worse));
        }

        @Test
        void getBestOptionsReturnsCappedSortedList() {
            plan.addTripOption(direct(30), comparator);
            plan.addTripOption(direct(10), comparator);
            plan.addTripOption(direct(20), comparator);

            List<TripOption> top2 = plan.getBestOptions(2, comparator);
            assertEquals(2, top2.size());
            assertEquals(10, top2.get(0).getTotalTravelMinutes());
        }
    }

    @Nested
    class Queries {

        @BeforeEach
        void addOptions() {
            plan.addTripOption(direct(25), comparator);
            plan.addTripOption(direct(15), comparator);
            plan.addTripOption(oneTransfer(10), comparator);
        }

        @Test
        void getFastestOptionReturnsShortestTime() {
            assertEquals(15, plan.getFastestOption().getTotalTravelMinutes());
        }

        @Test
        void getOptionWithFewestTransfersPrefersDirect() {
            assertEquals(TripType.DIRECT, plan.getOptionWithFewestTransfers().getTripType());
        }

        @Test
        void getCheapestOptionExists() {
            assertNotNull(plan.getCheapestOption());
        }

        @Test
        void getDirectOptionsFiltersOnlyDirect() {
            List<TripOption> direct = plan.getDirectOptions();
            assertEquals(2, direct.size());
            assertTrue(direct.stream().allMatch(o -> o.getTripType() == TripType.DIRECT));
        }

        @Test
        void getTransferOptionsFiltersByTransferCount() {
            List<TripOption> transfers = plan.getTransferOptions();
            assertEquals(1, transfers.size());
            assertTrue(transfers.stream().allMatch(o -> o.getTransfersCount() > 0));
        }

        @Test
        void getTripOptionsReturnsIndependentCopy() {
            List<TripOption> copy = plan.getTripOptions();
            copy.clear();
            assertEquals(3, plan.getTripOptionsCount());
        }
    }

    @Nested
    class Statistics {

        @Test
        void emptyPlanReturnsZeroStatistics() {
            TripPlan.TripPlanStatistics stats = plan.getStatistics();
            assertEquals(0, stats.directOptionsCount());
            assertEquals(0, stats.transferOptionsCount());
            assertEquals(0, stats.fastestTimeMinutes());
            assertEquals(0.0, stats.averageCostManat());
        }

        @Test
        void statisticsCountsDirectVsTransfer() {
            plan.addTripOption(direct(15), comparator);
            plan.addTripOption(direct(20), comparator);
            plan.addTripOption(oneTransfer(10), comparator);

            TripPlan.TripPlanStatistics stats = plan.getStatistics();
            assertEquals(2, stats.directOptionsCount());
            assertEquals(1, stats.transferOptionsCount());
            assertEquals(15, stats.fastestTimeMinutes());
            assertTrue(stats.averageTimeMinutes() > 0);
            assertTrue(stats.averageCostManat() > 0);
        }
    }

    @Test
    void restoreRebuildsPlanWithoutEvents() {
        TripPlanId id = TripPlanId.generate();
        TripPlan restored = TripPlan.restore(id, ORIGIN, DEST,
                TripSearchCriteria.fastestRoute(), DEPART, 0,
                DEPART, DEPART, 3L);
        assertEquals(id, restored.getId());
        assertEquals(3L, restored.getVersion());
        assertEquals(0, restored.getDomainEvents().size());
        assertEquals(0, restored.getTripOptionsCount());
    }
}
