package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FavoriteSpecificationsTest {

    @Nested
    class StopFavorites {

        private final ClientId clientA = ClientId.generate();
        private final ClientId clientB = ClientId.generate();
        private final BusStopId stop1 = BusStopId.generate();
        private final BusStopId stop2 = BusStopId.generate();

        private StopFavorite fav(ClientId client, BusStopId stop, LocalDateTime createdAt) {
            StopFavorite f = StopFavorite.create(client, stop);
            f.setCreatedAt(createdAt);
            return f;
        }

        @Test
        void belongsToClientMatches() {
            StopFavorite f = fav(clientA, stop1, LocalDateTime.now());
            assertTrue(StopFavoriteSpecifications.belongsToClient(clientA).isSatisfiedBy(f));
            assertFalse(StopFavoriteSpecifications.belongsToClient(clientB).isSatisfiedBy(f));
        }

        @Test
        void isForStopMatches() {
            StopFavorite f = fav(clientA, stop1, LocalDateTime.now());
            assertTrue(StopFavoriteSpecifications.isForStop(stop1).isSatisfiedBy(f));
            assertFalse(StopFavoriteSpecifications.isForStop(stop2).isSatisfiedBy(f));
        }

        @Test
        void belongsToClientAndStopRequiresBoth() {
            StopFavorite f = fav(clientA, stop1, LocalDateTime.now());
            assertTrue(StopFavoriteSpecifications.belongsToClientAndStop(clientA, stop1).isSatisfiedBy(f));
            assertFalse(StopFavoriteSpecifications.belongsToClientAndStop(clientA, stop2).isSatisfiedBy(f));
        }

        @Test
        void createdAfterAndBefore() {
            StopFavorite f = fav(clientA, stop1, LocalDateTime.now().minusDays(3));
            assertTrue(StopFavoriteSpecifications.createdAfter(LocalDateTime.now().minusDays(10)).isSatisfiedBy(f));
            assertTrue(StopFavoriteSpecifications.createdBefore(LocalDateTime.now().minusDays(1)).isSatisfiedBy(f));
            assertFalse(StopFavoriteSpecifications.createdAfter(LocalDateTime.now()).isSatisfiedBy(f));
        }

        @Test
        void createdAfterHandlesNullTimestampGracefully() {
            StopFavorite noTs = StopFavorite.create(clientA, stop1);
            assertFalse(StopFavoriteSpecifications.createdAfter(LocalDateTime.now().minusDays(1))
                    .isSatisfiedBy(noTs));
        }

        @Test
        void createdWithinDaysIsRelative() {
            StopFavorite recent = fav(clientA, stop1, LocalDateTime.now().minusDays(3));
            StopFavorite old = fav(clientA, stop1, LocalDateTime.now().minusDays(60));
            assertTrue(StopFavoriteSpecifications.createdWithinDays(7).isSatisfiedBy(recent));
            assertFalse(StopFavoriteSpecifications.createdWithinDays(7).isSatisfiedBy(old));
        }

        @Test
        void recentFavoritesForClientCombinesPredicates() {
            StopFavorite myRecent = fav(clientA, stop1, LocalDateTime.now().minusDays(2));
            StopFavorite othersRecent = fav(clientB, stop1, LocalDateTime.now().minusDays(2));
            StopFavorite myOld = fav(clientA, stop2, LocalDateTime.now().minusDays(100));

            var spec = StopFavoriteSpecifications.recentFavoritesForClient(clientA, 7);
            assertTrue(spec.isSatisfiedBy(myRecent));
            assertFalse(spec.isSatisfiedBy(othersRecent));
            assertFalse(spec.isSatisfiedBy(myOld));
        }
    }

    @Nested
    class RouteFavorites {

        private final ClientId clientA = ClientId.generate();
        private final BusRouteId route1 = BusRouteId.generate();
        private final BusRouteId route2 = BusRouteId.generate();

        private RouteFavorite fav(ClientId client, BusRouteId route, LocalDateTime createdAt) {
            RouteFavorite f = RouteFavorite.create(client, route);
            f.setCreatedAt(createdAt);
            return f;
        }

        @Test
        void belongsToClientMatches() {
            RouteFavorite f = fav(clientA, route1, LocalDateTime.now());
            assertTrue(RouteFavoriteSpecifications.belongsToClient(clientA).isSatisfiedBy(f));
        }

        @Test
        void isForRouteMatches() {
            RouteFavorite f = fav(clientA, route1, LocalDateTime.now());
            assertTrue(RouteFavoriteSpecifications.isForRoute(route1).isSatisfiedBy(f));
            assertFalse(RouteFavoriteSpecifications.isForRoute(route2).isSatisfiedBy(f));
        }

        @Test
        void belongsToClientAndRouteRequiresBoth() {
            RouteFavorite f = fav(clientA, route1, LocalDateTime.now());
            assertTrue(RouteFavoriteSpecifications.belongsToClientAndRoute(clientA, route1).isSatisfiedBy(f));
            assertFalse(RouteFavoriteSpecifications.belongsToClientAndRoute(clientA, route2).isSatisfiedBy(f));
        }

        @Test
        void createdAfterAndBefore() {
            RouteFavorite f = fav(clientA, route1, LocalDateTime.now().minusDays(3));
            assertTrue(RouteFavoriteSpecifications.createdAfter(LocalDateTime.now().minusDays(10)).isSatisfiedBy(f));
            assertTrue(RouteFavoriteSpecifications.createdBefore(LocalDateTime.now().minusDays(1)).isSatisfiedBy(f));
        }

        @Test
        void createdWithinDays() {
            RouteFavorite recent = fav(clientA, route1, LocalDateTime.now().minusDays(1));
            assertTrue(RouteFavoriteSpecifications.createdWithinDays(7).isSatisfiedBy(recent));
        }

        @Test
        void recentFavoritesForClientCombinesPredicates() {
            RouteFavorite recent = fav(clientA, route1, LocalDateTime.now().minusDays(2));
            assertTrue(RouteFavoriteSpecifications.recentFavoritesForClient(clientA, 7).isSatisfiedBy(recent));
        }
    }
}
