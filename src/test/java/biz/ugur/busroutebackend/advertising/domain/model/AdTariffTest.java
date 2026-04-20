package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.events.AdTariffCreatedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdTariffToggledEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdTariffUpdatedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdTariffTest {

    private TariffName name;
    private Price price;
    private AdTariff fresh;

    @BeforeEach
    void setUp() {
        name = TariffName.of("Weekly Basic");
        price = Price.ofMinor(5000, "TMT");
        fresh = AdTariff.create(name, "Weekly placement", PlacementType.BANNER,
                TariffPeriod.WEEK, price, 10_000, 1_000, 2_000, 1);
    }

    @Nested
    class Create {

        @Test
        void createsActiveWithCreatedEvent() {
            assertNotNull(fresh.getId());
            assertEquals(name, fresh.getName());
            assertEquals("Weekly placement", fresh.getDescription());
            assertEquals(PlacementType.BANNER, fresh.getPlacementType());
            assertEquals(TariffPeriod.WEEK, fresh.getPeriod());
            assertEquals(price, fresh.getPrice());
            assertEquals(10_000, fresh.getMaxImpressions());
            assertEquals(1_000, fresh.getMaxClicks());
            assertEquals(2_000, fresh.getDailyImpressionCap());
            assertTrue(fresh.getIsActive());
            assertEquals(1, fresh.getDisplayOrder());
            assertEquals(0L, fresh.getVersion());
            assertEquals(1, fresh.getDomainEvents().size());
            assertInstanceOf(AdTariffCreatedEvent.class, fresh.getDomainEvents().get(0));
        }

        @Test
        void createTrimsDescriptionAndNullsEmpty() {
            AdTariff t = AdTariff.create(name, "   ", PlacementType.BANNER, TariffPeriod.WEEK, price,
                    null, null, null, null);
            assertNull(t.getDescription());
            assertNull(t.getMaxImpressions());
            assertEquals(0, t.getDisplayOrder());
        }

        @Test
        void createRejectsNullName() {
            assertThrows(AdvertisingValidationException.class,
                    () -> AdTariff.create(null, "desc", PlacementType.BANNER, TariffPeriod.WEEK, price,
                            null, null, null, null));
        }

        @Test
        void createRejectsNullPlacementType() {
            assertThrows(AdvertisingValidationException.class,
                    () -> AdTariff.create(name, "desc", null, TariffPeriod.WEEK, price,
                            null, null, null, null));
        }

        @Test
        void createRejectsNullPeriod() {
            assertThrows(AdvertisingValidationException.class,
                    () -> AdTariff.create(name, "desc", PlacementType.BANNER, null, price,
                            null, null, null, null));
        }

        @Test
        void createRejectsNullPrice() {
            assertThrows(AdvertisingValidationException.class,
                    () -> AdTariff.create(name, "desc", PlacementType.BANNER, TariffPeriod.WEEK, null,
                            null, null, null, null));
        }

        @Test
        void createRejectsNegativeMaxImpressions() {
            assertThrows(AdvertisingValidationException.class,
                    () -> AdTariff.create(name, "desc", PlacementType.BANNER, TariffPeriod.WEEK, price,
                            -1, null, null, null));
        }
    }

    @Nested
    class TariffNameValueObject {

        @Test
        void rejectsTooShort() {
            assertThrows(AdvertisingValidationException.class, () -> TariffName.of("x"));
        }

        @Test
        void rejectsTooLong() {
            String longName = "x".repeat(201);
            assertThrows(AdvertisingValidationException.class, () -> TariffName.of(longName));
        }

        @Test
        void rejectsNull() {
            assertThrows(AdvertisingValidationException.class, () -> TariffName.of(null));
        }

        @Test
        void trimsValue() {
            assertEquals("ab", TariffName.of("  ab  ").getValue());
        }
    }

    @Nested
    class Update {

        @Test
        void updatesNameAndEmitsEventWithNameChange() {
            TariffName renamed = TariffName.of("Weekly Premium");
            AdTariff updated = fresh.update(renamed, null, null, null, null, null, null);
            assertEquals(renamed, updated.getName());
            AdTariffUpdatedEvent ev = (AdTariffUpdatedEvent) updated.getDomainEvents().get(0);
            assertTrue(ev.getChanges().containsKey("name"));
            assertFalse(ev.getChanges().containsKey("priceAmount"));
        }

        @Test
        void updatePriceRecordsAmountAndCurrency() {
            Price newPrice = Price.ofMinor(7500, "USD");
            AdTariff updated = fresh.update(null, null, newPrice, null, null, null, null);
            AdTariffUpdatedEvent ev = (AdTariffUpdatedEvent) updated.getDomainEvents().get(0);
            assertTrue(ev.getChanges().containsKey("priceAmount"));
            assertTrue(ev.getChanges().containsKey("currency"));
        }

        @Test
        void updateWithUnchangedValuesEmitsNoEvent() {
            AdTariff updated = fresh.update(name, "Weekly placement", price, 10_000, 1_000, 2_000, 1);
            assertEquals(0, updated.getDomainEvents().size());
        }

        @Test
        void updateDescriptionToBlankClearsIt() {
            AdTariff updated = fresh.update(null, "   ", null, null, null, null, null);
            assertNull(updated.getDescription());
            AdTariffUpdatedEvent ev = (AdTariffUpdatedEvent) updated.getDomainEvents().get(0);
            assertTrue(ev.getChanges().containsKey("description"));
        }

        @Test
        void updateRejectsNegativeCounters() {
            assertThrows(AdvertisingValidationException.class,
                    () -> fresh.update(null, null, null, -5, null, null, null));
        }
    }

    @Nested
    class ActivationLifecycle {

        @Test
        void deactivateFlipsAndEmitsToggled() {
            AdTariff off = fresh.deactivate();
            assertFalse(off.getIsActive());
            assertEquals(1, off.getDomainEvents().size());
            assertInstanceOf(AdTariffToggledEvent.class, off.getDomainEvents().get(0));
            AdTariffToggledEvent ev = (AdTariffToggledEvent) off.getDomainEvents().get(0);
            assertFalse(ev.isActive());
        }

        @Test
        void deactivateIdempotent() {
            AdTariff off = fresh.deactivate();
            assertSame(off, off.deactivate());
        }

        @Test
        void activateIdempotent() {
            assertSame(fresh, fresh.activate());
        }

        @Test
        void activateAfterDeactivateEmitsActivated() {
            AdTariff off = fresh.deactivate();
            AdTariff on = off.activate();
            AdTariffToggledEvent ev = (AdTariffToggledEvent) on.getDomainEvents().get(0);
            assertTrue(ev.isActive());
        }
    }

    @Test
    void restoreRebuildsStateWithoutEvents() {
        AdTariff restored = AdTariff.restore(
                fresh.getId(), name, "Saved", PlacementType.BANNER, TariffPeriod.WEEK,
                price, 100, 10, 5, false, 2,
                fresh.getCreatedAt(), fresh.getUpdatedAt(), 4L
        );
        assertEquals(fresh.getId(), restored.getId());
        assertFalse(restored.getIsActive());
        assertEquals(4L, restored.getVersion());
        assertEquals(0, restored.getDomainEvents().size());
    }

    @Test
    void restoreDefaultsVersionWhenNull() {
        AdTariff restored = AdTariff.restore(
                TariffId.generate(), name, null, PlacementType.BANNER, TariffPeriod.WEEK,
                price, null, null, null, true, 0,
                null, null, null
        );
        assertEquals(0L, restored.getVersion());
    }
}
