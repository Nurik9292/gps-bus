package biz.ugur.busroutebackend.geospatial.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.GarageBoundary;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GarageTest {

    private static final Coordinates DEPOT = Coordinates.of(37.96, 58.32);

    @Nested
    class Create {

        @Test
        void createsActiveGarageWithDefaults() {
            Garage g = Garage.create("Depot 1", "Ammar 1", "Depot One", DEPOT, 300, "city-001");
            assertEquals("Depot 1", g.getName());
            assertEquals("Ammar 1", g.getNameTm());
            assertEquals("Depot One", g.getNameEn());
            assertEquals(DEPOT, g.getLocation());
            assertEquals(300, g.getRadiusMeters());
            assertEquals("city-001", g.getCityId());
            assertTrue(g.getIsActive());
            assertNotNull(g.getId());
        }

        @Test
        void rejectsNullName() {
            assertThrows(NullPointerException.class,
                    () -> Garage.create(null, "tm", "en", DEPOT, 300, "city"));
        }

        @Test
        void rejectsNullLocation() {
            assertThrows(NullPointerException.class,
                    () -> Garage.create("name", "tm", "en", null, 300, "city"));
        }

        @Test
        void rejectsNullRadius() {
            assertThrows(NullPointerException.class,
                    () -> Garage.create("name", "tm", "en", DEPOT, null, "city"));
        }

        @Test
        void rejectsOutOfBoundRadius() {
            assertThrows(IllegalArgumentException.class,
                    () -> Garage.create("name", "tm", "en", DEPOT, 0, "city"));
            assertThrows(IllegalArgumentException.class,
                    () -> Garage.create("name", "tm", "en", DEPOT, -1, "city"));
            assertThrows(IllegalArgumentException.class,
                    () -> Garage.create("name", "tm", "en", DEPOT, 1001, "city"));
        }

        @Test
        void radiusBoundariesInclusive() {
            assertDoesNotThrow(() -> Garage.create("name", "tm", "en", DEPOT, 1, "city"));
            assertDoesNotThrow(() -> Garage.create("name", "tm", "en", DEPOT, 1000, "city"));
        }
    }

    @Test
    void hasBoundaryFalseWhenMissing() {
        Garage g = Garage.create("Depot", "tm", "en", DEPOT, 300, "city");
        assertFalse(g.hasBoundary());
    }

    @Test
    void hasBoundaryTrueWhenValidPolygonSet() {
        GarageBoundary boundary = GarageBoundary.of(List.of(
                Coordinates.of(37.96, 58.32),
                Coordinates.of(37.96, 58.34),
                Coordinates.of(37.98, 58.34),
                Coordinates.of(37.98, 58.32)));
        Garage g = new Garage.Builder()
                .id(new GarageId())
                .name("Depot").nameTm("tm").nameEn("en")
                .location(DEPOT).radiusMeters(300).cityId("city")
                .isActive(true).boundary(boundary).build();
        assertTrue(g.hasBoundary());
    }

    @Test
    void timestampsAndVersionAreMutable() {
        Garage g = Garage.create("Depot", "tm", "en", DEPOT, 300, "city");
        LocalDateTime t = LocalDateTime.now();
        g.setCreatedAt(t);
        g.setUpdatedAt(t);
        g.setVersion(5L);
        assertEquals(t, g.getCreatedAt());
        assertEquals(t, g.getUpdatedAt());
        assertEquals(5L, g.getVersion());
    }

    @Nested
    class GarageIdTests {

        @Test
        void noArgConstructorGeneratesRandomUuid() {
            GarageId id = new GarageId();
            assertNotNull(id.getValue());
        }

        @Test
        void generateFactoryProducesId() {
            assertNotNull(GarageId.generate().getValue());
        }

        @Test
        void ofStringValidatesUuid() {
            UUID uuid = UUID.randomUUID();
            GarageId id = GarageId.of(uuid.toString());
            assertEquals(uuid, id.getValue());
        }

        @Test
        void ofUuidAcceptsDirectInput() {
            UUID uuid = UUID.randomUUID();
            GarageId id = GarageId.of(uuid);
            assertEquals(uuid, id.getValue());
        }

        @Test
        void stringConstructorRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> new GarageId(""));
            assertThrows(IllegalArgumentException.class, () -> new GarageId("   "));
            assertThrows(IllegalArgumentException.class, () -> new GarageId((String) null));
        }

        @Test
        void uuidConstructorRejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> new GarageId((UUID) null));
        }

        @Test
        void toStringEchoesUuid() {
            UUID uuid = UUID.randomUUID();
            assertEquals(uuid.toString(), new GarageId(uuid).toString());
        }

        @Test
        void equalityBasedOnUuid() {
            UUID uuid = UUID.randomUUID();
            assertEquals(new GarageId(uuid), new GarageId(uuid));
        }
    }
}
