package biz.ugur.busroutebackend.banner.application.dto;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO для комплексного поиска баннеров с использованием Specification Pattern.
 *
 * Позволяет комбинировать различные критерии поиска:
 * - Фильтрация по типу (MAIN, POPUP, PLACES)
 * - Фильтрация по статусу активности
 * - Текстовый поиск по названию
 * - Фильтрация по периоду действия
 * - Фильтрация по диапазону display_order
 * - Фильтрация по дате создания
 * - Поиск истекающих баннеров
 *
 * Пример использования:
 * <pre>
 * // Найти активные баннеры типа MAIN, содержащие "акция" в названии
 * SearchBannersQuery query = SearchBannersQuery.builder()
 *     .type(BannerType.MAIN)
 *     .isActive(true)
 *     .titleSearch("акция")
 *     .build();
 * </pre>
 */
@Data
@Builder
public class SearchBannersQuery {

    /**
     * Тип баннера для фильтрации (MAIN, POPUP, PLACES).
     * Если null, тип не учитывается.
     */
    private BannerType type;

    /**
     * Фильтр по активности.
     * true - только активные, false - только неактивные, null - все.
     */
    private Boolean isActive;

    /**
     * Текст для поиска в названии баннера (case-insensitive).
     * Если null, поиск по названию не применяется.
     */
    private String titleSearch;

    /**
     * Фильтр по текущей активности периода.
     * true - только баннеры с активным периодом на момент запроса.
     * false/null - период не проверяется.
     */
    private Boolean periodActive;

    /**
     * Минимальный displayOrder (включительно).
     * Используется вместе с maxDisplayOrder для фильтрации по приоритету.
     */
    private Integer minDisplayOrder;

    /**
     * Максимальный displayOrder (включительно).
     * Используется вместе с minDisplayOrder для фильтрации по приоритету.
     */
    private Integer maxDisplayOrder;

    /**
     * Фильтр по дате создания (баннеры созданные после указанной даты).
     * Если null, дата создания не учитывается.
     */
    private LocalDateTime createdAfter;

    /**
     * Поиск баннеров, период которых истекает в течение N дней.
     * Если null, не применяется.
     * Полезно для уведомлений администратора.
     */
    private Integer expiringWithinDays;

    /**
     * Параметры пагинации.
     */
    private int page = 1;
    private int size = 25;

    /**
     * Поле для сортировки.
     */
    private String sortField;

    /**
     * Направление сортировки (ASC/DESC).
     */
    private String sortOrder;

    /**
     * Валидация параметров запроса.
     *
     * @throws IllegalArgumentException если параметры некорректны
     */
    public void validate() {
        validatePagination();
        validateSortOrder();
        validateDisplayOrderRange();
        validateExpiringDays();
    }

    private void validatePagination() {
        if (page < 1) {
            throw new IllegalArgumentException("Page number must be greater than 0, got: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100, got: " + size);
        }
    }

    private void validateSortOrder() {
        if (sortOrder != null && !sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("Sort order must be 'asc' or 'desc', got: " + sortOrder);
        }
    }

    private void validateDisplayOrderRange() {
        if (minDisplayOrder != null && maxDisplayOrder != null && minDisplayOrder > maxDisplayOrder) {
            throw new IllegalArgumentException(
                String.format("minDisplayOrder (%d) cannot be greater than maxDisplayOrder (%d)",
                    minDisplayOrder, maxDisplayOrder)
            );
        }
    }

    private void validateExpiringDays() {
        if (expiringWithinDays != null && expiringWithinDays < 1) {
            throw new IllegalArgumentException("expiringWithinDays must be at least 1, got: " + expiringWithinDays);
        }
    }

    /**
     * Проверка наличия хотя бы одного критерия поиска.
     *
     * @return true если есть хотя бы один критерий фильтрации
     */
    public boolean hasAnyCriteria() {
        return type != null
            || isActive != null
            || titleSearch != null
            || periodActive != null
            || minDisplayOrder != null
            || maxDisplayOrder != null
            || createdAfter != null
            || expiringWithinDays != null;
    }
}
