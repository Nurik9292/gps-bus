package biz.ugur.busroutebackend.payment.domain.enums;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentDomainEnumsTest {

    @Nested
    class PaymentProviderTests {

        @Test
        void fromAcceptsExactMatch() {
            assertEquals(PaymentProvider.RYSGAL, PaymentProvider.from("RYSGAL"));
            assertEquals(PaymentProvider.SENAGAT, PaymentProvider.from("SENAGAT"));
            assertEquals(PaymentProvider.BKB, PaymentProvider.from("BKB"));
            assertEquals(PaymentProvider.HALK, PaymentProvider.from("HALK"));
        }

        @Test
        void fromIsCaseInsensitiveAndTrims() {
            assertEquals(PaymentProvider.RYSGAL, PaymentProvider.from("  rysgal  "));
        }

        @Test
        void fromRejectsBlank() {
            assertThrows(PaymentValidationException.class, () -> PaymentProvider.from(null));
            assertThrows(PaymentValidationException.class, () -> PaymentProvider.from("  "));
        }

        @Test
        void fromRejectsUnknown() {
            assertThrows(PaymentValidationException.class, () -> PaymentProvider.from("visa"));
        }

        @Test
        void electronicProvidersUseSvEpgProtocol() {
            for (PaymentProvider p : PaymentProvider.values()) {
                if (p == PaymentProvider.CASH) continue;
                assertEquals("sv_epg", p.getProtocol(), p.name());
                assertTrue(p.isElectronic(), p.name());
            }
        }

        @Test
        void cashProviderIsNotElectronic() {
            assertEquals("cash", PaymentProvider.CASH.getProtocol());
            assertFalse(PaymentProvider.CASH.isElectronic());
        }
    }

    @Nested
    class PaymentSubjectTypeTests {

        @Test
        void fromAcceptsAdPlacement() {
            assertEquals(PaymentSubjectType.AD_PLACEMENT, PaymentSubjectType.from("AD_PLACEMENT"));
        }

        @Test
        void fromIsCaseInsensitiveAndTrims() {
            assertEquals(PaymentSubjectType.AD_PLACEMENT, PaymentSubjectType.from("  ad_placement  "));
        }

        @Test
        void fromRejectsBlank() {
            assertThrows(PaymentValidationException.class, () -> PaymentSubjectType.from(null));
            assertThrows(PaymentValidationException.class, () -> PaymentSubjectType.from(""));
        }

        @Test
        void fromRejectsUnknown() {
            assertThrows(PaymentValidationException.class, () -> PaymentSubjectType.from("subscription"));
        }
    }
}
