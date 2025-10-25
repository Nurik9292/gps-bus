package biz.ugur.busroutebackend.shared.domain.specification;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Представляет SQL условие с параметрами для безопасных запросов.
 * Используется для преобразования Specification в SQL WHERE clause.
 */
@Getter
@AllArgsConstructor
public class SqlCriteria {

    /**
     * SQL WHERE условие с плейсхолдерами (например: "type = :type AND is_active = :isActive")
     */
    private final String whereClause;

    /**
     * Параметры для подстановки в плейсхолдеры (например: {"type": "MAIN", "isActive": true})
     */
    private final Map<String, Object> parameters;

    /**
     * Создает пустое условие (без фильтрации).
     */
    public static SqlCriteria empty() {
        return new SqlCriteria("1=1", Collections.emptyMap());
    }

    /**
     * Создает условие с одним параметром.
     *
     * @param whereClause SQL условие
     * @param paramName имя параметра
     * @param paramValue значение параметра
     * @return новый SqlCriteria
     */
    public static SqlCriteria of(String whereClause, String paramName, Object paramValue) {
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, paramValue);
        return new SqlCriteria(whereClause, params);
    }

    /**
     * Комбинирует два условия через AND.
     *
     * @param other другое условие
     * @return новое условие, объединяющее оба через AND
     */
    public SqlCriteria and(SqlCriteria other) {
        if (this.whereClause.equals("1=1")) {
            return other;
        }
        if (other.whereClause.equals("1=1")) {
            return this;
        }

        String combinedClause = "(" + this.whereClause + ") AND (" + other.whereClause + ")";
        Map<String, Object> combinedParams = new HashMap<>(this.parameters);
        combinedParams.putAll(other.parameters);

        return new SqlCriteria(combinedClause, combinedParams);
    }

    /**
     * Комбинирует два условия через OR.
     *
     * @param other другое условие
     * @return новое условие, объединяющее оба через OR
     */
    public SqlCriteria or(SqlCriteria other) {
        if (this.whereClause.equals("1=1")) {
            return other;
        }
        if (other.whereClause.equals("1=1")) {
            return this;
        }

        String combinedClause = "(" + this.whereClause + ") OR (" + other.whereClause + ")";
        Map<String, Object> combinedParams = new HashMap<>(this.parameters);
        combinedParams.putAll(other.parameters);

        return new SqlCriteria(combinedClause, combinedParams);
    }

    /**
     * Инвертирует условие через NOT.
     *
     * @return новое условие с NOT
     */
    public SqlCriteria not() {
        if (this.whereClause.equals("1=1")) {
            return new SqlCriteria("1=0", Collections.emptyMap());
        }

        String notClause = "NOT (" + this.whereClause + ")";
        return new SqlCriteria(notClause, this.parameters);
    }
}
