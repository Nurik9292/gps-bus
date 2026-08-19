package biz.ugur.busroutebackend.advertising.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity.AdPlacementEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AdPlacementColumnListsTest {

    private static final Path BASE_REPO = Path.of(
            "src/main/java/biz/ugur/busroutebackend/advertising/infrastructure/persistence/repository/AdPlacementBaseRepository.java");
    private static final Path R2DBC_REPO = Path.of(
            "src/main/java/biz/ugur/busroutebackend/advertising/infrastructure/persistence/repository/R2dbcAdPlacementRepository.java");

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static Set<String> entityColumns() {
        return Arrays.stream(AdPlacementEntity.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .map(AdPlacementColumnListsTest::camelToSnake)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String camelToSnake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    @Test
    void selectColumnsCoverEveryEntityField() throws Exception {
        String source = read(BASE_REPO);

        Set<String> missing = entityColumns().stream()
                .filter(column -> !source.contains("\"" + column + "\""))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(missing)
                .withFailMessage("Поля entity, забытые в SELECT_COLUMNS/mapEntityToColumns/mapRow: %s", missing)
                .isEmpty();
    }

    @Test
    void prefixedSelectListStaysInSyncWithPlainOne() throws Exception {
        String prefixed = read(R2DBC_REPO);

        Set<String> missing = entityColumns().stream()
                .filter(column -> !prefixed.contains("\"p." + column + "\""))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(missing)
                .withFailMessage("Колонки, забытые в SELECT_COLUMNS_PREFIXED (уронят мобильную витрину баннеров): %s", missing)
                .isEmpty();
    }

    @Test
    void writeAndReadListsMentionSourceColumns() throws Exception {
        String source = read(BASE_REPO);

        assertThat(source).contains("columns.put(\"source\"");
        assertThat(source).contains("columns.put(\"external_service_id\"");
        assertThat(source).contains("columns.put(\"external_ref\"");
        assertThat(source).contains("row.get(\"source\"");
        assertThat(source).contains("row.get(\"external_service_id\"");
        assertThat(source).contains("row.get(\"external_ref\"");
    }
}
