package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.time.LocalDateTime;

/**
 * Specification pattern implementations for StopFavorite aggregate.
 * Provides reusable, composable query conditions following SOLID principles (Open/Closed Principle).
 *
 * Each specification can be used standalone or combined using and(), or(), not() methods.
 * Dual implementation: in-memory (isSatisfiedBy) + SQL (toSqlCriteria) for flexibility.
 */
public final class StopFavoriteSpecifications {

    private StopFavoriteSpecifications() {
        // Utility class - prevent instantiation
    }

    // ============= Client Specifications =============

    /**
     * Stop favorites for specific client.
     */
    public static Specification<StopFavorite> belongsToClient(ClientId clientId) {
        return new Specification<StopFavorite>() {
            @Override
            public boolean isSatisfiedBy(StopFavorite stopFavorite) {
                return clientId.equals(stopFavorite.getClientId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("client_id = :clientId", "clientId", clientId.getValue());
            }
        };
    }

    // ============= Stop Specifications =============

    /**
     * Stop favorites for specific stop.
     */
    public static Specification<StopFavorite> isForStop(BusStopId stopId) {
        return new Specification<StopFavorite>() {
            @Override
            public boolean isSatisfiedBy(StopFavorite stopFavorite) {
                return stopId.equals(stopFavorite.getStopId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("stop_id = :stopId", "stopId", stopId.getValue());
            }
        };
    }

    /**
     * Stop favorites for specific client and stop combination.
     */
    public static Specification<StopFavorite> belongsToClientAndStop(ClientId clientId, BusStopId stopId) {
        return new Specification<StopFavorite>() {
            @Override
            public boolean isSatisfiedBy(StopFavorite stopFavorite) {
                return clientId.equals(stopFavorite.getClientId()) &&
                       stopId.equals(stopFavorite.getStopId());
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                java.util.Map<String, Object> params = new java.util.HashMap<>();
                params.put("clientId", clientId.getValue());
                params.put("stopId", stopId.getValue());
                return new SqlCriteria("client_id = :clientId AND stop_id = :stopId", params);
            }
        };
    }

    // ============= Date Range Specifications =============

    /**
     * Stop favorites created after specified date.
     */
    public static Specification<StopFavorite> createdAfter(LocalDateTime since) {
        return new Specification<StopFavorite>() {
            @Override
            public boolean isSatisfiedBy(StopFavorite stopFavorite) {
                return stopFavorite.getCreatedAt() != null &&
                       stopFavorite.getCreatedAt().isAfter(since);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at > :createdAfter", "createdAfter", since);
            }
        };
    }

    /**
     * Stop favorites created before specified date.
     */
    public static Specification<StopFavorite> createdBefore(LocalDateTime until) {
        return new Specification<StopFavorite>() {
            @Override
            public boolean isSatisfiedBy(StopFavorite stopFavorite) {
                return stopFavorite.getCreatedAt() != null &&
                       stopFavorite.getCreatedAt().isBefore(until);
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("created_at < :createdBefore", "createdBefore", until);
            }
        };
    }

    /**
     * Stop favorites created within specified days.
     */
    public static Specification<StopFavorite> createdWithinDays(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return createdAfter(since);
    }

    // ============= Composite Specifications =============

    /**
     * Recent stop favorites for specific client.
     */
    public static Specification<StopFavorite> recentFavoritesForClient(ClientId clientId, int days) {
        return belongsToClient(clientId).and(createdWithinDays(days));
    }
}
