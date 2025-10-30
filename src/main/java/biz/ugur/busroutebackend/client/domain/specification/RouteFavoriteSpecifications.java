package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;

import java.time.LocalDateTime;

/**
 * Specification pattern implementations for RouteFavorite aggregate.
 * Provides reusable, composable query conditions following SOLID principles (Open/Closed Principle).
 *
 * Each specification can be used standalone or combined using and(), or(), not() methods.
 * Dual implementation: in-memory (isSatisfiedBy) + SQL (toSqlCriteria) for flexibility.
 */
public final class RouteFavoriteSpecifications {

    private RouteFavoriteSpecifications() {
        // Utility class - prevent instantiation
    }

    // ============= Client Specifications =============

    /**
     * Route favorites for specific client.
     */
    public static Specification<RouteFavorite> belongsToClient(ClientId clientId) {
        return new Specification<RouteFavorite>() {
            @Override
            public boolean isSatisfiedBy(RouteFavorite routeFavorite) {
                return clientId.equals(routeFavorite.getClientId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("client_id = :clientId", "clientId", clientId.getValue());
            }
        };
    }

    // ============= Route Specifications =============

    /**
     * Route favorites for specific route.
     */
    public static Specification<RouteFavorite> isForRoute(BusRouteId routeId) {
        return new Specification<RouteFavorite>() {
            @Override
            public boolean isSatisfiedBy(RouteFavorite routeFavorite) {
                return routeId.equals(routeFavorite.getRouteId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("route_id = :routeId", "routeId", routeId.getValue());
            }
        };
    }

    /**
     * Route favorites for specific client and route combination.
     */
    public static Specification<RouteFavorite> belongsToClientAndRoute(ClientId clientId, BusRouteId routeId) {
        return new Specification<RouteFavorite>() {
            @Override
            public boolean isSatisfiedBy(RouteFavorite routeFavorite) {
                return clientId.equals(routeFavorite.getClientId()) &&
                       routeId.equals(routeFavorite.getRouteId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                java.util.Map<String, Object> params = new java.util.HashMap<>();
                params.put("clientId", clientId.getValue());
                params.put("routeId", routeId.getValue());
                return new SqlCriteria("client_id = :clientId AND route_id = :routeId", params);
            }
        };
    }

    // ============= Date Range Specifications =============

    /**
     * Route favorites created after specified date.
     */
    public static Specification<RouteFavorite> createdAfter(LocalDateTime since) {
        return new Specification<RouteFavorite>() {
            @Override
            public boolean isSatisfiedBy(RouteFavorite routeFavorite) {
                return routeFavorite.getCreatedAt() != null &&
                       routeFavorite.getCreatedAt().isAfter(since);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", since);
            }
        };
    }

    /**
     * Route favorites created before specified date.
     */
    public static Specification<RouteFavorite> createdBefore(LocalDateTime until) {
        return new Specification<RouteFavorite>() {
            @Override
            public boolean isSatisfiedBy(RouteFavorite routeFavorite) {
                return routeFavorite.getCreatedAt() != null &&
                       routeFavorite.getCreatedAt().isBefore(until);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at < :createdBefore", "createdBefore", until);
            }
        };
    }

    /**
     * Route favorites created within specified days.
     */
    public static Specification<RouteFavorite> createdWithinDays(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return createdAfter(since);
    }

    // ============= Composite Specifications =============

    /**
     * Recent route favorites for specific client.
     */
    public static Specification<RouteFavorite> recentFavoritesForClient(ClientId clientId, int days) {
        return belongsToClient(clientId).and(createdWithinDays(days));
    }
}
