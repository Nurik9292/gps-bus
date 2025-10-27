package biz.ugur.busroutebackend.shared.domain.specification;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public class SqlCriteria {

    private final String whereClause;

    private final Map<String, Object> parameters;

    public static SqlCriteria empty() {
        return new SqlCriteria("1=1", Collections.emptyMap());
    }

    public static SqlCriteria of(String whereClause, String paramName, Object paramValue) {
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, paramValue);
        return new SqlCriteria(whereClause, params);
    }

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

    public SqlCriteria not() {
        if (this.whereClause.equals("1=1")) {
            return new SqlCriteria("1=0", Collections.emptyMap());
        }

        String notClause = "NOT (" + this.whereClause + ")";
        return new SqlCriteria(notClause, this.parameters);
    }
}
