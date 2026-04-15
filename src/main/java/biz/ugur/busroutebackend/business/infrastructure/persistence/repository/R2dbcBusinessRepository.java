package biz.ugur.busroutebackend.business.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcBusinessRepository extends BusinessBaseRepository implements BusinessRepository {

    public R2dbcBusinessRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<Business> findByStatus(BusinessStatus status, Pageable pageable) {
        String sql = String.format("""
                SELECT * FROM businesses
                WHERE status = :status
                %s
                LIMIT :limit OFFSET :offset
                """, getOrderByClause(pageable));
        return databaseClient.sql(sql)
                .bind("status", status.name())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByStatus(BusinessStatus status) {
        return databaseClient.sql("SELECT COUNT(*) FROM businesses WHERE status = :status")
                .bind("status", status.name())
                .map(row -> row.get(0, Long.class))
                .one();
    }

    @Override
    public Flux<Business> findByType(BusinessType type, Pageable pageable) {
        String sql = String.format("""
                SELECT * FROM businesses
                WHERE business_type = :type
                %s
                LIMIT :limit OFFSET :offset
                """, getOrderByClause(pageable));
        return databaseClient.sql(sql)
                .bind("type", type.name())
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Boolean> existsByTaxNumber(String taxNumber) {
        return databaseClient.sql("SELECT EXISTS (SELECT 1 FROM businesses WHERE tax_number = :tax)")
                .bind("tax", taxNumber)
                .map(row -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsByContactPhone(String phone) {
        return databaseClient.sql("SELECT EXISTS (SELECT 1 FROM businesses WHERE contact_phone = :phone)")
                .bind("phone", phone)
                .map(row -> row.get(0, Boolean.class))
                .one();
    }
}
