package biz.ugur.busroutebackend.shared.infrastructure.external.gps.mapper;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.TugdkGpsResponseDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;


@Component
@Slf4j
public class TugdkGpsPositionMapper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<GpsPositionDTO> mapAll(List<TugdkGpsResponseDTO> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        return responses.stream()
                .filter(TugdkGpsResponseDTO::isValidPosition)
                .map(this::map)
                .filter(Objects::nonNull)
                .toList();
    }

    public GpsPositionDTO map(TugdkGpsResponseDTO source) {
        if (source == null || !source.isValidPosition()) {
            log.trace("Skipping invalid TUGDK position: {}", source);
            return null;
        }

        try {
            GpsPositionDTO dto = new GpsPositionDTO();

            dto.setGpsProvider(GpsProviderType.TUGDK);
            dto.setDeviceId(source.getAttributes().getUniqueId());

            dto.setLatitude(source.getLatitude());
            dto.setLongitude(source.getLongitude());

            dto.setSpeed(source.getSpeedKmh());

            dto.setCourse(source.getCourse());

            LocalDateTime fixTime = parseFixTime(source.getFixTime());
            if (fixTime == null) {
                return null;
            }
            dto.setFixTime(fixTime);

            dto.setReportTime(fixTime.format(REPORT_TIME_FORMATTER));

            dto.setUtcTime(source.getFixTime());

            dto.setServerTime(parseUtcOffsetTime(source.getServerTime()));
            dto.setDeviceTime(parseUtcOffsetTime(source.getDeviceTime()));
            dto.setAccuracy(source.getAccuracy());

            if (source.getAttributes() != null) {
                dto.setHdop(source.getAttributes().getHdop());
                dto.setSatellites(source.getAttributes().getSat());
            }

            GpsPositionDTO.GpsAttributesDTO attributes = new GpsPositionDTO.GpsAttributesDTO();
            attributes.setUniqueId(source.getAttributes().getUniqueId());
            attributes.setName(normalizeLicensePlate(source.getAttributes().getName()));
            attributes.setMotion(source.getAttributes().getMotion());
            attributes.setIgnition(source.getAttributes().isIgnitionOn());
            dto.setAttributes(attributes);

            log.trace("Mapped TUGDK position: deviceId={}, plate={}, coords=({}, {})",
                    dto.getDeviceId(),
                    attributes.getName(),
                    dto.getLatitude(),
                    dto.getLongitude());

            return dto;

        } catch (Exception e) {
            log.warn("Failed to map TUGDK position for device {}: {}",
                    source.getAttributes() != null ? source.getAttributes().getUniqueId() : "unknown",
                    e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseFixTime(String isoTime) {
        if (isoTime == null || isoTime.isBlank()) {
            return null;
        }

        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoTime, ISO_FORMATTER);
            return odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("[GPS_PIPELINE] Dropping TUGDK fix with unparseable fixTime '{}': {}",
                    isoTime, e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseUtcOffsetTime(String isoTime) {
        if (isoTime == null || isoTime.isBlank()) {
            return null;
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(isoTime, ISO_FORMATTER);
            return odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.debug("[GPS_PIPELINE] Ignoring unparseable time '{}': {}", isoTime, e.getMessage());
            return null;
        }
    }

    private String normalizeLicensePlate(String plate) {
        if (plate == null || plate.isBlank()) {
            return null;
        }

        String normalized = plate.trim()
                .replace("-", "")
                .replaceAll("\\s+", " ");

        return normalized.toUpperCase();
    }
}
