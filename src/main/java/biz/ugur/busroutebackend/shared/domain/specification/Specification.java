package biz.ugur.busroutebackend.shared.domain.specification;

/**
 * Базовый интерфейс для паттерна Specification.
 *
 * Specification Pattern позволяет инкапсулировать бизнес-правила и условия запросов
 * в переиспользуемые объекты, которые можно комбинировать.
 *
 * @param <T> Тип объекта, к которому применяется спецификация
 */
public interface Specification<T> {

    /**
     * Проверяет, удовлетворяет ли объект данной спецификации.
     * Используется для in-memory фильтрации.
     *
     * @param candidate объект для проверки
     * @return true, если объект удовлетворяет спецификации
     */
    boolean isSatisfiedBy(T candidate);

    /**
     * Комбинирует текущую спецификацию с другой через логическое И (AND).
     * Результат удовлетворяется только если обе спецификации истинны.
     *
     * @param other другая спецификация
     * @return новая спецификация, представляющая логическое И
     */
    default Specification<T> and(Specification<T> other) {
        return new AndSpecification<>(this, other);
    }

    /**
     * Комбинирует текущую спецификацию с другой через логическое ИЛИ (OR).
     * Результат удовлетворяется если хотя бы одна из спецификаций истинна.
     *
     * @param other другая спецификация
     * @return новая спецификация, представляющая логическое ИЛИ
     */
    default Specification<T> or(Specification<T> other) {
        return new OrSpecification<>(this, other);
    }

    /**
     * Возвращает инверсию текущей спецификации (NOT).
     * Результат удовлетворяется если текущая спецификация ложна.
     *
     * @return новая спецификация, представляющая логическое НЕ
     */
    default Specification<T> not() {
        return new NotSpecification<>(this);
    }

    /**
     * Преобразует спецификацию в SQL WHERE условие для R2DBC.
     *
     * @return объект SqlCriteria с SQL условием и параметрами
     */
    SqlCriteria toSqlCriteria();
}
