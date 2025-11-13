package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;

import java.time.LocalDateTime;

public final class RouteFavoriteSpecifications {

    private RouteFavoriteSpecifications() {
        // Utility class - prevent instantiation
    }


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

    public static Specification<RouteFavorite> createdWithinDays(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return createdAfter(since);
    }


    public static Specification<RouteFavorite> recentFavoritesForClient(ClientId clientId, int days) {
        return belongsToClient(clientId).and(createdWithinDays(days));
    }
}
