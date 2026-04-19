package biz.ugur.busroutebackend.place.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaceAliasAndStreetAliasTest {

    @Nested
    class StreetAliasTests {

        @Test
        void createTrimsAliasAndDefaultsLanguageToRu() {
            StreetAlias a = StreetAlias.create("street-1", "  Nepesa  ", null);
            assertEquals("Nepesa", a.getAlias());
            assertEquals("ru", a.getLanguage());
            assertEquals("street-1", a.getStreetId());
            assertNotNull(a.getId());
        }

        @Test
        void createAcceptsExplicitLanguage() {
            StreetAlias a = StreetAlias.create("s", "Street", "tm");
            assertEquals("tm", a.getLanguage());
        }

        @Test
        void createRejectsBlankAlias() {
            assertThrows(IllegalArgumentException.class,
                    () -> StreetAlias.create("s", "   ", "ru"));
            assertThrows(IllegalArgumentException.class,
                    () -> StreetAlias.create("s", null, "ru"));
        }

        @Test
        void updateAliasChangesValueAndKeepsLanguageWhenNull() {
            StreetAlias a = StreetAlias.create("s", "Street", "ru");
            StreetAlias updated = a.updateAlias("New Street", null);
            assertEquals("New Street", updated.getAlias());
            assertEquals("ru", updated.getLanguage());
        }

        @Test
        void updateAliasKeepsOldValueWhenNewAliasIsNull() {
            StreetAlias a = StreetAlias.create("s", "Street", "ru");
            StreetAlias updated = a.updateAlias(null, "tm");
            assertEquals("Street", updated.getAlias());
            assertEquals("tm", updated.getLanguage());
        }
    }

    @Nested
    class PlaceAliasTests {

        @Test
        void createTrimsAliasAndDefaultsLanguageToRu() {
            PlaceAlias a = PlaceAlias.create("place-1", "  Central  ", null);
            assertEquals("Central", a.getAlias());
            assertEquals("ru", a.getLanguage());
            assertNotNull(a.getId());
        }

        @Test
        void createRejectsBlankAlias() {
            assertThrows(IllegalArgumentException.class,
                    () -> PlaceAlias.create("p", "", "ru"));
        }

        @Test
        void updateAliasKeepsOldLanguageWhenNull() {
            PlaceAlias a = PlaceAlias.create("p", "Park", "ru");
            PlaceAlias updated = a.updateAlias("Central Park", null);
            assertEquals("Central Park", updated.getAlias());
            assertEquals("ru", updated.getLanguage());
        }

        @Test
        void updateAliasPropagatesNewLanguage() {
            PlaceAlias a = PlaceAlias.create("p", "Park", "ru");
            PlaceAlias updated = a.updateAlias("Baghi", "tm");
            assertEquals("Baghi", updated.getAlias());
            assertEquals("tm", updated.getLanguage());
        }
    }
}
