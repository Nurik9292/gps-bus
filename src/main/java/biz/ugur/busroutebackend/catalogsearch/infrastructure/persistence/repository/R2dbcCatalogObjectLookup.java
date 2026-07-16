package biz.ugur.busroutebackend.catalogsearch.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogObjectLookup;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcCatalogObjectLookup implements CatalogObjectLookup {

    private final DatabaseClient databaseClient;

    public R2dbcCatalogObjectLookup(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<String> findTitle(CatalogObjectKind kind, String objectId) {
        String sql = kind == CatalogObjectKind.STOP
                ? "SELECT stop_name AS title FROM bus_stops WHERE id = :id AND is_active"
                : "SELECT route_number AS title FROM bus_routes WHERE id = :id AND is_active";
        return databaseClient.sql(sql)
                .bind("id", objectId)
                .map(row -> row.get("title", String.class))
                .one();
    }
}
