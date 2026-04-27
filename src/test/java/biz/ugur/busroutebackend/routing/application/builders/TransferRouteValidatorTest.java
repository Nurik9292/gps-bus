package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TransferRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferRouteValidatorTest {

    private TransferRouteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransferRouteValidator();
    }

    private BusRoute route(String number) {
        return BusRoute.create(number, "Name " + number, "Name " + number, "Name " + number, "#000000", "city", 30);
    }

    private BusStop stop(String name) {
        return BusStop.create(name, name, name,
                StopCode.of(name + "-CODE"),
                new BigDecimal("37.95"), new BigDecimal("58.35"),
                false, "city-001", "admin");
    }

    private TransferRouteResult oneTransfer(String firstRouteNumber, String secondRouteNumber,
                                            int firstMinutes, int waitMinutes, int secondMinutes) {
        return new TransferRouteResult(
                route(firstRouteNumber), stop("from"), stop("transfer"),
                route(secondRouteNumber), stop("to"),
                firstMinutes, waitMinutes, secondMinutes,
                100.0, 100.0
        );
    }

    private TwoTransferRouteResult twoTransfer(String r1, String r2, String r3) {
        return new TwoTransferRouteResult(
                route(r1), stop("from"), stop("t1"),
                route(r2), stop("t2"),
                route(r3), stop("to"),
                10, 5, 10, 5, 10,
                100.0, 100.0
        );
    }

    @Nested
    class OneTransfer {

        @Test
        void rejectsDuplicateRoute() {
            TransferRouteResult sameOnBothLegs = oneTransfer("15", "15", 10, 5, 10);

            assertFalse(validator.isOneTransferRouteViable(sameOnBothLegs));
        }

        @Test
        void acceptsDifferentRoutes() {
            TransferRouteResult differentRoutes = oneTransfer("15", "23", 10, 5, 10);

            assertTrue(validator.isOneTransferRouteViable(differentRoutes));
        }

        @Test
        void rejectsExcessiveTotalTime() {
            TransferRouteResult tooLong = oneTransfer("15", "23", 60, 10, 50);

            assertFalse(validator.isOneTransferRouteViable(tooLong));
        }

        @Test
        void rejectsExcessiveWaitTime() {
            TransferRouteResult longWait = oneTransfer("15", "23", 10, 35, 10);

            assertFalse(validator.isOneTransferRouteViable(longWait));
        }

        @Test
        void rejectsTooShortLeg() {
            TransferRouteResult shortFirstLeg = oneTransfer("15", "23", 0, 5, 10);

            assertFalse(validator.isOneTransferRouteViable(shortFirstLeg));
        }
    }

    @Nested
    class TwoTransfer {

        @Test
        void rejectsAdjacentDuplicateRoutes() {
            TwoTransferRouteResult firstTwoSame = twoTransfer("15", "15", "23");

            assertFalse(validator.isTwoTransferRouteViable(firstTwoSame));
        }

        @Test
        void rejectsNonAdjacentDuplicateRoutes() {
            TwoTransferRouteResult firstAndThirdSame = twoTransfer("15", "23", "15");

            assertFalse(validator.isTwoTransferRouteViable(firstAndThirdSame));
        }

        @Test
        void acceptsAllDistinctRoutes() {
            TwoTransferRouteResult distinct = twoTransfer("15", "23", "29");

            assertTrue(validator.isTwoTransferRouteViable(distinct));
        }
    }
}
