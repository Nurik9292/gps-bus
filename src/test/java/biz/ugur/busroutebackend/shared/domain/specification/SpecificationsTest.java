package biz.ugur.busroutebackend.shared.domain.specification;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpecificationsTest {

    private static Specification<String> isNotBlank() {
        return new Specification<>() {
            @Override
            public boolean isSatisfiedBy(String candidate) {
                return candidate != null && !candidate.isBlank();
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("value IS NOT NULL AND value <> ''", "marker", "blank");
            }
        };
    }

    private static Specification<String> startsWithA() {
        return new Specification<>() {
            @Override
            public boolean isSatisfiedBy(String candidate) {
                return candidate != null && candidate.startsWith("A");
            }

            @Override
            public SqlCriteria toSqlCriteria() {
                return SqlCriteria.of("value LIKE :prefix", "prefix", "A%");
            }
        };
    }

    @Nested
    class SqlCriteriaTests {

        @Test
        void emptyReturnsTautology() {
            SqlCriteria c = SqlCriteria.empty();
            assertEquals("1=1", c.getWhereClause());
            assertTrue(c.getParameters().isEmpty());
        }

        @Test
        void ofStoresSingleParameter() {
            SqlCriteria c = SqlCriteria.of("id = :id", "id", 42);
            assertEquals("id = :id", c.getWhereClause());
            assertEquals(42, c.getParameters().get("id"));
        }

        @Test
        void andMergesClausesAndParameters() {
            SqlCriteria a = SqlCriteria.of("a = :a", "a", 1);
            SqlCriteria b = SqlCriteria.of("b = :b", "b", 2);
            SqlCriteria result = a.and(b);
            assertTrue(result.getWhereClause().contains("AND"));
            assertEquals(1, result.getParameters().get("a"));
            assertEquals(2, result.getParameters().get("b"));
        }

        @Test
        void andShortCircuitsTautology() {
            SqlCriteria real = SqlCriteria.of("x = :x", "x", 1);
            assertEquals("x = :x", SqlCriteria.empty().and(real).getWhereClause());
            assertEquals("x = :x", real.and(SqlCriteria.empty()).getWhereClause());
        }

        @Test
        void orMergesClauses() {
            SqlCriteria a = SqlCriteria.of("a = :a", "a", 1);
            SqlCriteria b = SqlCriteria.of("b = :b", "b", 2);
            SqlCriteria result = a.or(b);
            assertTrue(result.getWhereClause().contains("OR"));
        }

        @Test
        void orShortCircuitsTautology() {
            SqlCriteria real = SqlCriteria.of("y = :y", "y", 1);
            assertEquals("y = :y", SqlCriteria.empty().or(real).getWhereClause());
            assertEquals("y = :y", real.or(SqlCriteria.empty()).getWhereClause());
        }

        @Test
        void notWrapsExistingClause() {
            SqlCriteria a = SqlCriteria.of("a = :a", "a", 1);
            SqlCriteria negated = a.not();
            assertTrue(negated.getWhereClause().startsWith("NOT"));
            assertEquals(1, negated.getParameters().get("a"));
        }

        @Test
        void notOfTautologyReturnsContradiction() {
            assertEquals("1=0", SqlCriteria.empty().not().getWhereClause());
        }

        @Test
        void constructorPreservesParameters() {
            SqlCriteria c = new SqlCriteria("raw = :x", Map.of("x", 5));
            assertEquals("raw = :x", c.getWhereClause());
            assertEquals(5, c.getParameters().get("x"));
        }
    }

    @Nested
    class CompositeSpecifications {

        @Test
        void andSpecSatisfiedOnlyWhenBothMatch() {
            Specification<String> spec = isNotBlank().and(startsWithA());
            assertTrue(spec.isSatisfiedBy("Alpha"));
            assertFalse(spec.isSatisfiedBy(""));
            assertFalse(spec.isSatisfiedBy("Beta"));
            assertTrue(spec.toSqlCriteria().getWhereClause().contains("AND"));
        }

        @Test
        void orSpecSatisfiedWhenEitherMatches() {
            Specification<String> spec = startsWithA().or(isNotBlank());
            assertTrue(spec.isSatisfiedBy("Alpha"));
            assertTrue(spec.isSatisfiedBy("hello"));
            assertFalse(spec.isSatisfiedBy(""));
            assertTrue(spec.toSqlCriteria().getWhereClause().contains("OR"));
        }

        @Test
        void notSpecInverts() {
            Specification<String> spec = isNotBlank().not();
            assertTrue(spec.isSatisfiedBy(""));
            assertTrue(spec.isSatisfiedBy(null));
            assertFalse(spec.isSatisfiedBy("hi"));
            assertTrue(spec.toSqlCriteria().getWhereClause().startsWith("NOT"));
        }

        @Test
        void andChainsCombinePredicates() {
            Specification<String> spec = isNotBlank().and(startsWithA());
            assertFalse(spec.isSatisfiedBy(null));
            assertTrue(spec.isSatisfiedBy("Alpha"));
        }

        @Test
        void notOfAndGivesDeMorganEffect() {
            Specification<String> combined = isNotBlank().and(startsWithA()).not();
            assertTrue(combined.isSatisfiedBy("Beta"));
            assertFalse(combined.isSatisfiedBy("Alpha"));
        }
    }
}
