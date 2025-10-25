package biz.ugur.busroutebackend.shared.domain.specification;

/**
 * Композитная спецификация для логического НЕ (NOT).
 * Удовлетворяется когда внутренняя спецификация ложна.
 *
 * @param <T> тип объекта
 */
public class NotSpecification<T> implements Specification<T> {

    private final Specification<T> specification;

    public NotSpecification(Specification<T> specification) {
        this.specification = specification;
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return !specification.isSatisfiedBy(candidate);
    }

    @Override
    public SqlCriteria toSqlCriteria() {
        return specification.toSqlCriteria().not();
    }
}
