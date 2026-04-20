package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StopCodeTest {

    @Test
    void generateUsesAshgabatPrefix() {
        StopCode code = StopCode.generate("city-001", 7);
        assertEquals("ASH007", code.getValue());
    }

    @Test
    void generateUsesDefaultPrefixForUnknownCity() {
        StopCode code = StopCode.generate("city-999", 12);
        assertEquals("ST012", code.getValue());
    }

    @Test
    void generateWithSuffixAppendsTwoDigits() {
        StopCode code = StopCode.generateWithSuffix("city-002", 3, 5);
        assertEquals("BAL00305", code.getValue());
    }

    @Test
    void ofPreservesValueExactly() {
        StopCode code = StopCode.of("MAR123");
        assertEquals("MAR123", code.getValue());
    }

    @Test
    void ofRejectsNull() {
        assertThrows(NullPointerException.class, () -> StopCode.of(null));
    }

    @Test
    void getCityPrefixReturnsFirstThreeChars() {
        assertEquals("TUR", StopCode.of("TUR450").getCityPrefix());
    }

    @Test
    void belongsToCityDetectsAshgabatPrefix() {
        StopCode code = StopCode.of("ASH007");
        assertTrue(code.belongsToCity("city-001"));
        assertFalse(code.belongsToCity("city-004"));
    }

    @Test
    void nextInSequenceKeepsPrefix() {
        StopCode code = StopCode.of("BAL010");
        assertEquals("BAL999", code.nextInSequence().getValue());
    }

    @Test
    void equalityBasedOnValue() {
        StopCode a = StopCode.of("ASH001");
        StopCode b = StopCode.of("ASH001");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
