package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LicensePlateExtractorTest {

    private LicensePlateExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new LicensePlateExtractor(new VehicleValidationService());
    }

    @Test
    void extractFromStringReturnsPlateForValidInput() {
        assertEquals(Optional.of("1234 AGH"), extractor.extractFromString("1234 AGH"));
        assertEquals(Optional.of("1234 AGH"), extractor.extractFromString("  1234 agh  "));
    }

    @Test
    void extractFromStringStripsDashesButDoesNotInsertSpaces() {
        assertTrue(extractor.extractFromString("1234-AGH").isEmpty());
    }

    @Test
    void extractFromStringEmptyForInvalidInput() {
        assertTrue(extractor.extractFromString("not-a-plate").isEmpty());
        assertTrue(extractor.extractFromString(null).isEmpty());
        assertTrue(extractor.extractFromString("   ").isEmpty());
    }

    @Test
    void extractFromGpsDataReturnsEmptyForNullDto() {
        assertTrue(extractor.extractFromGpsData(null).isEmpty());
    }

    @Test
    void extractFromGpsDataReturnsEmptyForMissingVehicleName() {
        GpsPositionDTO gps = new GpsPositionDTO();
        assertTrue(extractor.extractFromGpsData(gps).isEmpty());

        GpsPositionDTO.GpsAttributesDTO empty = new GpsPositionDTO.GpsAttributesDTO();
        gps.setAttributes(empty);
        assertTrue(extractor.extractFromGpsData(gps).isEmpty());
    }

    @Test
    void extractFromGpsDataExtractsValidPlate() {
        GpsPositionDTO gps = gpsWithVehicleName("1992 AGH");
        assertEquals(Optional.of("1992 AGH"), extractor.extractFromGpsData(gps));
    }

    @Test
    void extractOrDefaultReturnsExtractedWhenPresent() {
        GpsPositionDTO gps = gpsWithVehicleName("1992 AGH");
        assertEquals("1992 AGH", extractor.extractOrDefault(gps, "unknown"));
    }

    @Test
    void extractOrDefaultReturnsFallbackWhenMissing() {
        GpsPositionDTO gps = new GpsPositionDTO();
        assertEquals("unknown", extractor.extractOrDefault(gps, "unknown"));
        assertEquals("default", extractor.extractOrDefault(null, "default"));
    }

    private static GpsPositionDTO gpsWithVehicleName(String name) {
        GpsPositionDTO gps = new GpsPositionDTO();
        GpsPositionDTO.GpsAttributesDTO attrs = new GpsPositionDTO.GpsAttributesDTO();
        attrs.setName(name);
        gps.setAttributes(attrs);
        return gps;
    }
}
