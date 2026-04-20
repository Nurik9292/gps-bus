package biz.ugur.busroutebackend.routing.domain.valueobjects;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripOptionTest {

    private static final Coordinates ORIGIN = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates HUB = Coordinates.of(37.9651, 58.3311);
    private static final Coordinates DEST = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime DEPART = LocalDateTime.of(2026, 5, 1, 10, 0);

    private TripOption directRide(int busMinutes) {
        return new TripOption(TripType.DIRECT,
                List.of(RouteSegment.busRideSegment(ORIGIN, DEST, busMinutes, "29A")),
                0, DEPART);
    }

    private TripOption oneTransfer(int ride1, int wait, int ride2) {
        return new TripOption(TripType.ONE_TRANSFER,
                List.of(
                        RouteSegment.busRideSegment(ORIGIN, HUB, ride1, "1"),
                        RouteSegment.transferSegment(HUB, wait),
                        RouteSegment.busRideSegment(HUB, DEST, ride2, "2")
                ),
                0, DEPART);
    }

    @Nested
    class Create {

        @Test
        void directRideBuildsCorrectTotals() {
            TripOption t = directRide(15);
            assertEquals(TripType.DIRECT, t.getTripType());
            assertEquals(15, t.getTotalBusRideMinutes());
            assertEquals(0, t.getTotalWalkingMinutes());
            assertEquals(0, t.getTotalWaitingMinutes());
            assertEquals(0, t.getTransfersCount());
            assertEquals(15, t.getTotalTravelMinutes());
            assertNotNull(t.getOptionId());
        }

        @Test
        void oneTransferTripCountsTransfersAndWait() {
            TripOption t = oneTransfer(10, 3, 5);
            assertEquals(1, t.getTransfersCount());
            assertEquals(3, t.getTotalWaitingMinutes());
            assertEquals(15, t.getTotalBusRideMinutes());
            assertEquals(18, t.getTotalTravelMinutes());
        }

        @Test
        void initialWaitingAddsToTotal() {
            TripOption t = new TripOption(TripType.DIRECT,
                    List.of(RouteSegment.busRideSegment(ORIGIN, DEST, 10, "1")),
                    7, DEPART);
            assertEquals(7, t.getInitialWaitingMinutes());
            assertEquals(17, t.getTotalTravelMinutes());
        }

        @Test
        void initialWaitingIsCappedAt60() {
            TripOption t = new TripOption(TripType.DIRECT,
                    List.of(RouteSegment.busRideSegment(ORIGIN, DEST, 10, "1")),
                    120, DEPART);
            assertEquals(60, t.getInitialWaitingMinutes());
        }

        @Test
        void rejectsNegativeInitialWaiting() {
            assertThrows(IllegalArgumentException.class, () -> new TripOption(
                    TripType.DIRECT,
                    List.of(RouteSegment.busRideSegment(ORIGIN, DEST, 10, "1")),
                    -1, DEPART));
        }

        @Test
        void rejectsEmptySegments() {
            assertThrows(IllegalArgumentException.class, () -> new TripOption(
                    TripType.DIRECT, List.of(), 0, DEPART));
        }

        @Test
        void rejectsNullTripType() {
            assertThrows(IllegalArgumentException.class, () -> new TripOption(
                    null, List.of(RouteSegment.busRideSegment(ORIGIN, DEST, 10, "1")), 0, DEPART));
        }

        @Test
        void rejectsTripStartingWithTransfer() {
            assertThrows(IllegalArgumentException.class, () -> new TripOption(
                    TripType.DIRECT,
                    List.of(
                            RouteSegment.transferSegment(ORIGIN, 2),
                            RouteSegment.busRideSegment(ORIGIN, DEST, 10, "1")
                    ),
                    0, DEPART));
        }
    }

    @Nested
    class Comparisons {

        @Test
        void isFasterThanComparesTotalTravelMinutes() {
            TripOption fast = directRide(10);
            TripOption slow = directRide(25);
            assertTrue(fast.isFasterThan(slow));
            assertFalse(slow.isFasterThan(fast));
        }

        @Test
        void hasFewerTransfersThanComparesTransferCount() {
            TripOption direct = directRide(10);
            TripOption transfer = oneTransfer(5, 2, 5);
            assertTrue(direct.hasFewerTransfersThan(transfer));
            assertFalse(transfer.hasFewerTransfersThan(direct));
        }

        @Test
        void isCheaperThanComparesCost() {
            TripOption direct = directRide(10);
            TripOption transfer = oneTransfer(5, 2, 5);
            assertTrue(direct.isCheaperThan(transfer));
        }
    }

    @Nested
    class DerivedInfo {

        @Test
        void getUsedRoutesReturnsDistinctBusNumbers() {
            TripOption t = oneTransfer(5, 1, 5);
            assertEquals(List.of("1", "2"), t.getUsedRoutes());
        }

        @Test
        void getUsedRouteNumbersJoinsWithComma() {
            TripOption t = oneTransfer(5, 1, 5);
            assertEquals("1, 2", t.getUsedRouteNumbers());
        }

        @Test
        void getSummaryMentionsDirectLabelAndMinutes() {
            TripOption direct = directRide(15);
            String summary = direct.getSummary();
            assertTrue(summary.contains("29A"));
            assertTrue(summary.contains("15"));
        }

        @Test
        void getSummaryMentionsTransferCount() {
            TripOption transfer = oneTransfer(5, 2, 5);
            String summary = transfer.getSummary();
            assertTrue(summary.contains("1"));
            assertTrue(summary.contains("пересадк"));
        }

        @Test
        void getDetailedDescriptionMentionsCostAndDuration() {
            TripOption direct = directRide(15);
            String detailed = direct.getDetailedDescription();
            assertTrue(detailed.contains("маната"));
            assertTrue(detailed.contains("15"));
        }
    }

    @Nested
    class Scores {

        @Test
        void qualityScoreDecreasesWithMoreTransfers() {
            double direct = directRide(15).getQualityScore();
            double transfer = oneTransfer(5, 2, 5).getQualityScore();
            assertTrue(direct > transfer);
        }

        @Test
        void reliabilityScoreIsBetween05And1() {
            TripOption direct = directRide(10);
            double score = direct.getReliabilityScore();
            assertTrue(score >= 0.5);
            assertTrue(score <= 1.0);
        }

        @Test
        void reliabilityScoreDropsWithTransfers() {
            double direct = directRide(10).getReliabilityScore();
            double transfer = oneTransfer(5, 2, 5).getReliabilityScore();
            assertTrue(direct > transfer);
        }

        @Test
        void isAccessibleTrueForShortDirect() {
            assertTrue(directRide(10).isAccessible());
        }

        @Test
        void isAccessibleFalseForMoreThanOneTransfer() {
            TripOption many = new TripOption(TripType.TWO_TRANSFER,
                    List.of(
                            RouteSegment.busRideSegment(ORIGIN, HUB, 3, "1"),
                            RouteSegment.transferSegment(HUB, 2),
                            RouteSegment.busRideSegment(HUB, HUB, 3, "2"),
                            RouteSegment.transferSegment(HUB, 2),
                            RouteSegment.busRideSegment(HUB, DEST, 3, "3")
                    ),
                    0, DEPART);
            assertFalse(many.isAccessible());
        }
    }

    @Nested
    class ValidForTrip {

        @Test
        void directRideIsValidWhenCloseToOriginAndDestination() {
            TripOption t = directRide(10);
            assertTrue(t.isValidForTrip(ORIGIN, DEST));
        }

        @Test
        void isValidForTripFalseWhenFarFromOrigin() {
            TripOption t = directRide(10);
            Coordinates farOrigin = Coordinates.of(38.5, 59.0);
            assertFalse(t.isValidForTrip(farOrigin, DEST));
        }

        @Test
        void isValidForTripFalseWhenFarFromDestination() {
            TripOption t = directRide(10);
            Coordinates farDest = Coordinates.of(40.0, 60.0);
            assertFalse(t.isValidForTrip(ORIGIN, farDest));
        }
    }
}
