# Banner Event Versioning Guide

## Overview

The Banner bounded context implements **event versioning** to support event evolution and backward compatibility. This allows us to:

- Add new fields to events without breaking existing consumers
- Maintain multiple versions of the same event type
- Support event migration strategies
- Enable backward compatibility in event replay scenarios

## Architecture

### Base Event Class

All Banner domain events extend `BannerDomainEvent`, which includes:

```java
public abstract class BannerDomainEvent {
    private final String eventId;
    private final String bannerId;
    private final Instant occurredAt;
    private final int eventVersion;  // ← Version field

    protected abstract int getCurrentVersion();
}
```

### Event Version Structure

Each event class defines its current version as a constant:

```java
public class BannerCreatedEvent extends BannerDomainEvent {
    public static final int CURRENT_VERSION = 1;

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
```

## Current Event Versions

| Event Type | Current Version | Notes |
|------------|----------------|-------|
| `BannerCreatedEvent` | 1 | Initial version |
| `BannerUpdatedEvent` | 1 | Initial version with changes map |
| `BannerActivatedEvent` | 1 | Initial version |
| `BannerDeactivatedEvent` | 1 | Initial version |
| `BannerDeletedEvent` | 1 | Initial version with optional reason |

## How to Evolve Events

### Scenario 1: Adding a New Field

When you need to add a new field to an existing event:

**Step 1: Create new version of the event class**

```java
public class BannerCreatedEventV2 extends BannerDomainEvent {
    public static final int CURRENT_VERSION = 2;

    // Original fields
    private final String title;
    private final String type;
    // ... other fields

    // NEW field in v2
    private final String createdBy;  // ← New field

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }
}
```

**Step 2: Update Event Store deserialization**

In `R2dbcBannerEventStore.mapRowToEvent()`:

```java
case "BannerCreatedEvent" -> {
    // Use version to determine which constructor to use
    if (version == 1) {
        yield new BannerCreatedEvent(/* v1 fields */);
    } else if (version == 2) {
        yield new BannerCreatedEventV2(/* v2 fields */);
    }
    // Add more versions as needed
}
```

**Step 3: Update serialization** (if needed)

In `R2dbcBannerEventStore.serializeEventPayload()`:

```java
case BannerCreatedEventV2 e -> objectMapper.writeValueAsString(Map.of(
    // Include all v2 fields
    "createdBy", e.getCreatedBy()
));
```

### Scenario 2: Changing Field Types

When you need to change a field's type:

**Original V1:**
```java
private final Integer displayOrder;
```

**New V2:**
```java
private final DisplayOrder displayOrder; // Value object instead of Integer
```

**Migration strategy:**
- Keep V1 class as-is for deserializing old events
- Create V2 class with new field type
- Provide a method to upgrade from V1 to V2:

```java
public BannerCreatedEventV2 toV2() {
    return new BannerCreatedEventV2(
        /* ... */,
        DisplayOrder.of(this.displayOrder) // Convert Integer → DisplayOrder
    );
}
```

### Scenario 3: Event Upcasting

For event replay/migration, implement an upcaster:

```java
public interface EventUpcaster {
    boolean canUpcast(BannerDomainEvent event);
    BannerDomainEvent upcast(BannerDomainEvent event);
}

public class BannerCreatedEventUpcaster implements EventUpcaster {
    @Override
    public boolean canUpcast(BannerDomainEvent event) {
        return event instanceof BannerCreatedEvent
            && event.getEventVersion() == 1;
    }

    @Override
    public BannerDomainEvent upcast(BannerDomainEvent event) {
        BannerCreatedEvent v1 = (BannerCreatedEvent) event;
        return new BannerCreatedEventV2(
            v1.getEventId(),
            v1.getBannerId(),
            v1.getOccurredAt(),
            2, // New version
            v1.getTitle(),
            // ... other fields
            "SYSTEM" // Default value for new field
        );
    }
}
```

## Best Practices

### 1. **Never Change Existing Events**
❌ DON'T modify existing event classes to add/remove fields
✅ DO create a new version of the event

### 2. **Keep Old Versions**
❌ DON'T delete old event version classes
✅ DO keep them for deserializing historical events

### 3. **Version Constants**
Always use constants for versions:
```java
public static final int CURRENT_VERSION = 2;
```

### 4. **Backward Compatibility**
The event store provides default version 1 for old events:
```java
// Default to version 1 for backward compatibility
int version = eventVersion != null ? eventVersion : 1;
```

### 5. **Documentation**
Document each version with a comment:
```java
/**
 * Event published when a new Banner is created.
 * Version 1 - Initial version.
 * Version 2 - Added createdBy field (2025-10-27).
 */
```

## Testing Event Versioning

### Test Deserialization of Old Events

```java
@Test
void shouldDeserializeV1Event() {
    // Simulate old event from database with version 1
    BannerDomainEvent event = eventStore.findById(eventId).block();

    assertThat(event.getEventVersion()).isEqualTo(1);
    assertThat(event).isInstanceOf(BannerCreatedEvent.class);
}

@Test
void shouldDeserializeV2Event() {
    // Simulate new event with version 2
    BannerDomainEvent event = eventStore.findById(eventId).block();

    assertThat(event.getEventVersion()).isEqualTo(2);
    assertThat(event).isInstanceOf(BannerCreatedEventV2.class);
}
```

### Test Event Upcasting

```java
@Test
void shouldUpcastV1ToV2() {
    BannerCreatedEvent v1Event = /* create v1 event */;
    BannerCreatedEventV2 v2Event = upcaster.upcast(v1Event);

    assertThat(v2Event.getEventVersion()).isEqualTo(2);
    assertThat(v2Event.getCreatedBy()).isEqualTo("SYSTEM"); // Default value
}
```

## Migration Strategies

### Strategy 1: Lazy Migration
- Old events stay at version 1
- New events use version 2
- Application handles both versions
- **Pro:** No downtime
- **Con:** Must support multiple versions indefinitely

### Strategy 2: Eager Migration
- Run a migration script to upcast all old events
- Application only needs to support latest version
- **Pro:** Simpler application code
- **Con:** Requires downtime or complex zero-downtime migration

### Strategy 3: Hybrid
- Support last N versions (e.g., v2 and v3)
- Periodically migrate very old events (v1 → v2)
- **Pro:** Balance between simplicity and compatibility
- **Con:** Requires periodic maintenance

## Example: Complete Event Evolution

### Version 1 (Initial - 2025-10-27)
```java
public class BannerCreatedEvent extends BannerDomainEvent {
    public static final int CURRENT_VERSION = 1;

    private final String title;
    private final String type;
    private final String imageUrl;
    // ... other fields
}
```

### Version 2 (Added metadata - 2025-11-15)
```java
public class BannerCreatedEventV2 extends BannerDomainEvent {
    public static final int CURRENT_VERSION = 2;

    // All V1 fields
    private final String title;
    private final String type;
    private final String imageUrl;

    // New in V2
    private final String createdBy;
    private final String sourceSystem;
}
```

### Version 3 (Refactored to value objects - 2025-12-01)
```java
public class BannerCreatedEventV3 extends BannerDomainEvent {
    public static final int CURRENT_VERSION = 3;

    // Refactored fields
    private final BannerTitle title;        // Was: String
    private final BannerType type;          // Was: String
    private final BannerImage imageUrl;     // Was: String

    // From V2
    private final UserId createdBy;         // Was: String
    private final String sourceSystem;
}
```

## Monitoring & Observability

Track event versions in logs:

```java
log.info("Saved event: {} v{} for banner: {}",
    event.getEventType(),
    event.getEventVersion(),
    event.getBannerId());
```

Create metrics for event version distribution:

```java
meterRegistry.counter("banner.events.version",
    "type", event.getEventType(),
    "version", String.valueOf(event.getEventVersion())
).increment();
```

## Summary

Event versioning provides:

✅ **Flexibility** - Evolve events without breaking existing consumers
✅ **Compatibility** - Support multiple versions simultaneously
✅ **Auditability** - Track which version of an event was stored
✅ **Migration** - Gradual migration paths for old events
✅ **Debugging** - Easier to diagnose issues with versioned events

Remember: **Events are facts that happened in the past. They should be immutable and versioned to preserve history accurately.**
