package biz.ugur.busroutebackend.admin.domain.specification;

import biz.ugur.busroutebackend.admin.domain.model.City;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CitySpecificationsTest {

    private City active() {
        City c = City.create("Ashgabat", "Asgabat", 1);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    private City inactiveCity() {
        City c = City.create("OldCity", "Koene", false, 100);
        c.setCreatedAt(LocalDateTime.now().minusDays(30));
        c.setUpdatedAt(LocalDateTime.now().minusDays(30));
        return c;
    }

    @Nested
    class StatusAndText {

        @Test
        void isActiveMatchesActive() {
            assertTrue(CitySpecifications.isActive().isSatisfiedBy(active()));
            assertFalse(CitySpecifications.isActive().isSatisfiedBy(inactiveCity()));
        }

        @Test
        void isInactiveMatchesInactive() {
            assertTrue(CitySpecifications.isInactive().isSatisfiedBy(inactiveCity()));
        }

        @Test
        void nameContainsIsCaseInsensitive() {
            assertTrue(CitySpecifications.nameContains("ash").isSatisfiedBy(active()));
            assertFalse(CitySpecifications.nameContains("unknown").isSatisfiedBy(active()));
        }

        @Test
        void nameTmContainsHandlesNull() {
            City noTm = City.create("City", null, 5);
            assertFalse(CitySpecifications.nameTmContains("any").isSatisfiedBy(noTm));
        }

        @Test
        void nameTmContainsMatchesSubstring() {
            assertTrue(CitySpecifications.nameTmContains("sgabat").isSatisfiedBy(active()));
        }

        @Test
        void hasNameMatchesExact() {
            assertTrue(CitySpecifications.hasName("Ashgabat").isSatisfiedBy(active()));
            assertFalse(CitySpecifications.hasName("ashgabat").isSatisfiedBy(active()));
        }

        @Test
        void hasIdMatchesGenerated() {
            City c = active();
            assertTrue(CitySpecifications.hasId(c.getId().getValue()).isSatisfiedBy(c));
            assertFalse(CitySpecifications.hasId("nope").isSatisfiedBy(c));
        }

        @Test
        void searchByTextMatchesEitherName() {
            assertTrue(CitySpecifications.searchByText("gab").isSatisfiedBy(active()));
            assertTrue(CitySpecifications.searchByText("Asgab").isSatisfiedBy(active()));
            assertFalse(CitySpecifications.searchByText("nothing").isSatisfiedBy(active()));
        }
    }

    @Nested
    class DisplayOrder {

        @Test
        void lessThanAndGreaterThan() {
            City early = active();
            assertTrue(CitySpecifications.displayOrderLessThan(10).isSatisfiedBy(early));
            assertFalse(CitySpecifications.displayOrderGreaterThan(10).isSatisfiedBy(early));
        }

        @Test
        void betweenInclusiveRange() {
            assertTrue(CitySpecifications.displayOrderBetween(0, 5).isSatisfiedBy(active()));
            assertFalse(CitySpecifications.displayOrderBetween(10, 20).isSatisfiedBy(active()));
        }
    }

    @Nested
    class Timestamps {

        @Test
        void createdAfterAndBefore() {
            City c = active();
            assertTrue(CitySpecifications.createdAfter(LocalDateTime.now().minusHours(1)).isSatisfiedBy(c));
            assertTrue(CitySpecifications.createdBefore(LocalDateTime.now().plusHours(1)).isSatisfiedBy(c));
        }

        @Test
        void updatedAfterPivot() {
            City c = active();
            assertTrue(CitySpecifications.updatedAfter(LocalDateTime.now().minusHours(1)).isSatisfiedBy(c));
        }

        @Test
        void isRecentlyCreatedDelegatesToCreatedAfter() {
            assertTrue(CitySpecifications.isRecentlyCreated(1).isSatisfiedBy(active()));
        }

        @Test
        void isRecentlyUpdatedDelegatesToUpdatedAfter() {
            assertTrue(CitySpecifications.isRecentlyUpdated(1).isSatisfiedBy(active()));
        }
    }

    @Nested
    class TurkmenName {

        @Test
        void hasTurkmenNameTrueWhenSet() {
            assertTrue(CitySpecifications.hasTurkmenName().isSatisfiedBy(active()));
        }

        @Test
        void hasTurkmenNameFalseWhenMissing() {
            City noTm = City.create("City", null, 5);
            assertFalse(CitySpecifications.hasTurkmenName().isSatisfiedBy(noTm));
        }

        @Test
        void noTurkmenNameNegatesPredicate() {
            City noTm = City.create("City", null, 5);
            assertTrue(CitySpecifications.noTurkmenName().isSatisfiedBy(noTm));
            assertFalse(CitySpecifications.noTurkmenName().isSatisfiedBy(active()));
        }
    }

    @Nested
    class Composite {

        @Test
        void isActiveWithHighPriorityRequiresBoth() {
            assertTrue(CitySpecifications.isActiveWithHighPriority().isSatisfiedBy(active()));
            assertFalse(CitySpecifications.isActiveWithHighPriority().isSatisfiedBy(inactiveCity()));
        }

        @Test
        void isCompleteAndActiveNeedsTurkmenAndActive() {
            assertTrue(CitySpecifications.isCompleteAndActive().isSatisfiedBy(active()));
            City noTm = City.create("City", null, 5);
            assertFalse(CitySpecifications.isCompleteAndActive().isSatisfiedBy(noTm));
        }
    }
}
