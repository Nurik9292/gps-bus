package biz.ugur.busroutebackend.complaint.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintStatus;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.complaint.domain.repository.ComplaintRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcComplaintRepository extends ComplaintBaseRepository implements ComplaintRepository {

    public R2dbcComplaintRepository(DatabaseClient databaseClient) {
        super(databaseClient);
    }

    @Override
    public Flux<Complaint> findByFilters(ComplaintStatus status, ComplaintType type, Pageable pageable) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(selectColumns())
                .append(" FROM complaints WHERE 1=1");

        if (status != null) sql.append(" AND status = :status");
        if (type != null) sql.append(" AND type = :type");

        sql.append(" ").append(getOrderByClause(pageable));
        sql.append(" LIMIT :limit OFFSET :offset");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        if (status != null) spec = spec.bind("status", status.name());
        if (type != null) spec = spec.bind("type", type.name());
        spec = spec.bind("limit", pageable.getPageSize());
        spec = spec.bind("offset", pageable.getOffset());

        return spec.map(getRowMapper()).all();
    }

    @Override
    public Mono<Long> countByFilters(ComplaintStatus status, ComplaintType type) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM complaints WHERE 1=1");

        if (status != null) sql.append(" AND status = :status");
        if (type != null) sql.append(" AND type = :type");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        if (status != null) spec = spec.bind("status", status.name());
        if (type != null) spec = spec.bind("type", type.name());

        return spec.map(row -> row.get(0, Long.class)).one();
    }

    @Override
    public Flux<Complaint> findByClientId(String clientId, Pageable pageable) {
        String sql = String.format(
                "SELECT %s FROM complaints WHERE client_id = :clientId %s LIMIT :limit OFFSET :offset",
                selectColumns(),
                getOrderByClause(pageable)
        );
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .bind("limit", pageable.getPageSize())
                .bind("offset", pageable.getOffset())
                .map(getRowMapper())
                .all();
    }

    @Override
    public Mono<Long> countByClientId(String clientId) {
        String sql = "SELECT COUNT(*) FROM complaints WHERE client_id = :clientId";
        return databaseClient.sql(sql)
                .bind("clientId", clientId)
                .map(row -> row.get(0, Long.class))
                .one();
    }
}
