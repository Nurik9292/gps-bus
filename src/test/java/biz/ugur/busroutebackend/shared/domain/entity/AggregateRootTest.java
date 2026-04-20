package biz.ugur.busroutebackend.shared.domain.entity;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AggregateRootTest {

    static class TestId {
        final String value;

        TestId(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TestId other)) return false;
            return java.util.Objects.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(value);
        }
    }

    static class TestAggregate extends AggregateRoot<TestAggregate, TestId> {
        private final TestId id;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Long version;

        TestAggregate(String idValue) {
            this.id = new TestId(idValue);
        }

        @Override
        public TestId getId() {
            return id;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        @Override
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Override
        public Long getVersion() {
            return version;
        }

        @Override
        public void setVersion(Long version) {
            this.version = version;
        }
    }

    static class TestEvent implements DomainEvent {
        final String name;

        TestEvent(String name) {
            this.name = name;
        }

        @Override
        public String getEventType() {
            return "TestEvent";
        }
    }

    @Test
    void registerEventAppendsToInternalList() {
        TestAggregate a = new TestAggregate("1");
        a.registerEvent(new TestEvent("first"));
        a.registerEvent(new TestEvent("second"));
        assertEquals(2, a.getDomainEvents().size());
    }

    @Test
    void getDomainEventsReturnsImmutableCopy() {
        TestAggregate a = new TestAggregate("1");
        a.registerEvent(new TestEvent("event"));
        java.util.List<DomainEvent> copy = a.getDomainEvents();
        assertThrows(UnsupportedOperationException.class, () -> copy.add(new TestEvent("x")));
    }

    @Test
    void clearEventsRemovesAll() {
        TestAggregate a = new TestAggregate("1");
        a.registerEvent(new TestEvent("first"));
        a.registerEvent(new TestEvent("second"));
        a.clearEvents();
        assertTrue(a.getDomainEvents().isEmpty());
    }

    @Test
    void equalsAndHashCodeBasedOnId() {
        TestAggregate a = new TestAggregate("same");
        TestAggregate b = new TestAggregate("same");
        TestAggregate c = new TestAggregate("different");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(null, a);
        assertNotEquals("string", a);
    }

    @Test
    void equalsReturnsTrueForSameInstance() {
        TestAggregate a = new TestAggregate("same");
        assertEquals(a, a);
    }

    @Test
    void timestampsAndVersionSettersWork() {
        TestAggregate a = new TestAggregate("1");
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 2, 1, 0, 0);

        a.setCreatedAt(created);
        a.setUpdatedAt(updated);
        a.setVersion(5L);

        assertEquals(created, a.getCreatedAt());
        assertEquals(updated, a.getUpdatedAt());
        assertEquals(5L, a.getVersion());
    }
}
