package biz.ugur.busroutebackend.client.domain.specification;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.specification.SqlCriteria;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.time.LocalDateTime;

public final class StopFavoriteSpecifications {

    private StopFavoriteSpecifications() {
    }


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

    public static Specification<StopFavorite> createdWithinDays(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return createdAfter(since);
    }


    public static Specification<StopFavorite> recentFavoritesForClient(ClientId clientId, int days) {
        return belongsToClient(clientId).and(createdWithinDays(days));
    }
}
