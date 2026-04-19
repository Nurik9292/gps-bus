package biz.ugur.busroutebackend.client.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ClientValueObjectsTest {

    @Test
    void clientIdGenerateProducesUuid() {
        ClientId id = ClientId.generate();
        assertEquals(36, id.getValue().length());
        assertEquals(id.getValue(), id.toString());
    }

    @Test
    void clientIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> ClientId.of(" "));
        assertThrows(IllegalArgumentException.class, () -> ClientId.of(null));
    }

    @Test
    void clientIdEqualityBasedOnValue() {
        ClientId a = ClientId.of("x");
        ClientId b = ClientId.of("x");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void phoneAcceptsCountryCodePrefixed() {
        Phone p = Phone.of("+99361234567");
        assertEquals("+993", p.getCountryCode());
        assertEquals("61234567", p.getNumber());
        assertEquals("+99361234567", p.getFormattedPhone());
    }

    @Test
    void phoneAcceptsImplicitCountryCode() {
        Phone p = Phone.of("61234567");
        assertEquals("+993", p.getCountryCode());
        assertEquals("61234567", p.getNumber());
    }

    @Test
    void phoneRejectsNull() {
        assertThrows(NullPointerException.class, () -> Phone.of(null));
    }

    @Test
    void phoneRejectsWrongFormat() {
        assertThrows(IllegalArgumentException.class, () -> Phone.of("12345"));
        assertThrows(IllegalArgumentException.class, () -> Phone.of("+1234567890"));
        assertThrows(IllegalArgumentException.class, () -> Phone.of("abc"));
    }

    @Test
    void phoneToStringMasksNothingByConvention() {
        Phone p = Phone.of("+99361234567");
        assertTrue(p.toString().contains("+99361234567"));
    }

    @Test
    void otpGenerateProducesFiveDigitUnverifiedCode() {
        Otp otp = Otp.generate();
        assertEquals(5, otp.getCode().length());
        assertFalse(otp.isVerified());
        assertFalse(otp.isExpired());
    }

    @Test
    void otpOfRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class,
                () -> Otp.of("1234", Instant.now(), false));
    }

    @Test
    void otpOfRejectsNonDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> Otp.of("abcde", Instant.now(), false));
    }

    @Test
    void otpIsExpiredWhenGeneratedInDistantPast() {
        Otp expired = Otp.of("12345", Instant.now().minusSeconds(3600), false);
        assertTrue(expired.isExpired());
    }

    @Test
    void otpVerifyRequiresMatchingUnexpiredUnverified() {
        Otp otp = Otp.of("12345", Instant.now(), false);
        assertTrue(otp.verify("12345"));
        assertFalse(otp.verify("99999"));

        Otp verified = otp.markAsVerified();
        assertFalse(verified.verify("12345"));
        assertTrue(verified.isVerified());
    }

    @Test
    void otpVerifyFailsAfterExpiry() {
        Otp old = Otp.of("12345", Instant.now().minusSeconds(3600), false);
        assertFalse(old.verify("12345"));
    }

    @Test
    void otpToStringMasksCode() {
        Otp otp = Otp.of("12345", Instant.now(), false);
        assertFalse(otp.toString().contains("12345"));
        assertTrue(otp.toString().contains("***"));
    }
}
