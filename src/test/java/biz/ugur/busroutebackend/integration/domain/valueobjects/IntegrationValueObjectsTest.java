package biz.ugur.busroutebackend.integration.domain.valueobjects;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationValueObjectsTest {

    @Nested
    class ExternalServiceIdTests {

        @Test
        void generateProducesUuid() {
            ExternalServiceId id = ExternalServiceId.generate();
            assertNotNull(id.getValue());
            assertEquals(36, id.getValue().length());
        }

        @Test
        void ofAcceptsStringAndUuid() {
            UUID uuid = UUID.randomUUID();
            ExternalServiceId fromString = ExternalServiceId.of(uuid.toString());
            ExternalServiceId fromUuid = ExternalServiceId.of(uuid);
            assertEquals(fromString, fromUuid);
        }

        @Test
        void ofRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> ExternalServiceId.of(""));
            assertThrows(IllegalArgumentException.class, () -> ExternalServiceId.of("   "));
            assertThrows(IllegalArgumentException.class, () -> ExternalServiceId.of((String) null));
        }

        @Test
        void toUuidRoundTrips() {
            UUID uuid = UUID.randomUUID();
            ExternalServiceId id = ExternalServiceId.of(uuid);
            assertEquals(uuid, id.toUUID());
        }

        @Test
        void equalityBasedOnValue() {
            UUID uuid = UUID.randomUUID();
            assertEquals(ExternalServiceId.of(uuid.toString()), ExternalServiceId.of(uuid.toString()));
        }
    }

    @Nested
    class ApiTokenTests {

        @Test
        void generateProducesBrtPrefixedValue() {
            ApiToken token = ApiToken.generate();
            assertTrue(token.getValue().startsWith("brt_"));
            assertTrue(token.getValue().length() >= 32);
        }

        @Test
        void ofAcceptsValidLongToken() {
            String raw = "x".repeat(40);
            ApiToken token = ApiToken.of(raw);
            assertEquals(raw, token.getValue());
        }

        @Test
        void ofRejectsShortToken() {
            assertThrows(IllegalArgumentException.class, () -> ApiToken.of("short"));
        }

        @Test
        void ofRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> ApiToken.of(""));
            assertThrows(IllegalArgumentException.class, () -> ApiToken.of("   "));
            assertThrows(IllegalArgumentException.class, () -> ApiToken.of(null));
        }

        @Test
        void maskedValueHidesMiddle() {
            String raw = "brt_abcdefghij1234567890abcdefgh5678";
            String masked = ApiToken.of(raw).getMaskedValue();
            assertTrue(masked.startsWith(raw.substring(0, 8)));
            assertTrue(masked.endsWith(raw.substring(raw.length() - 4)));
            assertTrue(masked.contains("..."));
        }

        @Test
        void maskedValueReturnsStarsForShortEnough() {
            String minimum = "x".repeat(32);
            String masked = ApiToken.of(minimum).getMaskedValue();
            assertNotEquals(minimum, masked);
        }

        @Test
        void toStringMasksValue() {
            ApiToken token = ApiToken.generate();
            assertFalse(token.toString().contains(token.getValue()),
                    "toString must not expose raw token");
        }

        @Test
        void equalityBasedOnValue() {
            String raw = "x".repeat(40);
            assertEquals(ApiToken.of(raw), ApiToken.of(raw));
        }
    }
}
