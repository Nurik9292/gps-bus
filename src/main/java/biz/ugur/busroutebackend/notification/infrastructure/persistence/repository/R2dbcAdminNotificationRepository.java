package biz.ugur.busroutebackend.notification.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcAdminNotificationRepository extends NotificationBaseRepository implements AdminNotificationRepository {


    public R2dbcAdminNotificationRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<Notification> findActiveNotifications() {
        String sql = String.format("""
            SELECT %s FROM notifications
            WHERE is_active = true
            ORDER BY display_order ASC, created_at DESC
            """, selectColumns());

        return databaseClient.sql(sql)
                .map(getRowMapper())
                .all();
    }


    @Override
    public Mono<Long> countActiveNotifications() {
        String sql = """
            SELECT COUNT(*) FROM notifications
            WHERE is_active = true
            """;

        return databaseClient.sql(sql)
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
