package biz.ugur.busroutebackend.shared.infrastructure.persistence;

import biz.ugur.busroutebackend.shared.base.BaseEntity;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class SortInjectionGuardTest {

    private static class StubEntity implements BaseEntity<String> {
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long version;

        @Override public String getId() { return "1"; }
        @Override public LocalDateTime getCreatedAt() { return createdAt; }
        @Override public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
        @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
        @Override public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
        @Override public Long getVersion() { return version; }
        @Override public void setVersion(Long value) { this.version = value; }
    }

    private static class StubRepository extends BaseR2dbcRepository<StubEntity, String> {

        StubRepository() {
            super(Mockito.mock(DatabaseClient.class), "stub_table", StubEntity.class);
        }

        @Override protected String selectColumns() {
            return "id, title, status, created_at";
        }

        @Override protected String convertIdToDatabase(String id) { return id; }

        @Override protected BiFunction<Row, RowMetadata, StubEntity> getRowMapper() {
            return (row, meta) -> new StubEntity();
        }

        @Override protected Map<String, Object> mapEntityToColumns(StubEntity entity) { return Map.of(); }

        String orderBy(String sortProperty) {
            return getOrderByClause(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, sortProperty)));
        }
    }

    private final StubRepository repository = new StubRepository();

    @Test
    void declaredColumnIsUsedForSorting() {
        assertThat(repository.orderBy("title")).isEqualTo("ORDER BY title ASC");
    }

    @Test
    void injectedStatementFallsBackToDefaultOrdering() {
        assertThat(repository.orderBy("id; DROP TABLE ad_placements--"))
                .isEqualTo("ORDER BY created_at DESC");
    }

    @Test
    void subqueryProbeFallsBackToDefaultOrdering() {
        assertThat(repository.orderBy("(SELECT password FROM admins LIMIT 1)"))
                .isEqualTo("ORDER BY created_at DESC");
    }

    @Test
    void columnOutsideDeclaredListIsIgnored() {
        assertThat(repository.orderBy("password")).isEqualTo("ORDER BY created_at DESC");
    }

    @Test
    void directionStaysUnderOurControl() {
        String clause = repository.getOrderByClause(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "status")));
        assertThat(clause).isEqualTo("ORDER BY status DESC");
    }
}
