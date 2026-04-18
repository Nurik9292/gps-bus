package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.event.*;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class BusStop extends AggregateRoot<BusStop, BusStopId> {

    private final BusStopId id;

    private final String stopName;
    private final String nameEn;
    private final String nameTm;
    private final String cityId;
    private final StopCode stopCode;

    private final BigDecimal latitude;
    private final BigDecimal longitude;

    private final Boolean isActive;
    private final Boolean isMajorStop;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    public static BusStop create(
            String stopName,
            String nameEn,
            String nameTm,
            StopCode stopCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean isMajorStop,
            String cityId,
            String createdBy) {

        String validatedStopName = validateStopName(stopName);
        String auditActor = (createdBy == null || createdBy.isBlank()) ? "anonymous" : createdBy;

        BusStop busStop = builder()
                .id(BusStopId.generate())
                .stopName(validatedStopName)
                .nameEn(nameEn)
                .nameTm(nameTm)
                .stopCode(stopCode)
                .latitude(latitude)
                .longitude(longitude)
                .isActive(true)
                .isMajorStop(isMajorStop != null ? isMajorStop : false)
                .cityId(cityId)
                .version(0L)
                .build();

        busStop.registerEvent(new BusStopCreatedEvent(
                busStop.id,
                busStop.stopName,
                busStop.nameEn,
                busStop.nameTm,
                busStop.stopCode,
                busStop.latitude,
                busStop.longitude,
                busStop.isMajorStop
        ));

        busStop.registerEvent(new BusStopRegisteredEvent(
                busStop.id,
                busStop.stopName,
                busStop.stopCode,
                auditActor
        ));

        return busStop;
    }

    public static BusStop restore(
            BusStopId id,
            String stopName,
            String nameEn,
            String nameTm,
            StopCode stopCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean isActive,
            Boolean isMajorStop,
            String cityId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return builder()
                .id(id)
                .stopName(stopName)
                .nameEn(nameEn)
                .nameTm(nameTm)
                .stopCode(stopCode)
                .latitude(latitude)
                .longitude(longitude)
                .isActive(isActive != null ? isActive : true)
                .isMajorStop(isMajorStop != null ? isMajorStop : false)
                .cityId(cityId)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }


    public BusStop updateInfo(
            String stopName,
            String nameEn,
            String nameTm,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean isActive,
            Boolean isMajorStop,
            String cityId) {

        boolean hasLocationChanged = !this.latitude.equals(latitude) || !this.longitude.equals(longitude);
        boolean hasNameChanged = !this.stopName.equals(stopName);

        String validatedStopName = validateStopName(stopName);

        BusStop updatedStop = this.toBuilder()
                .stopName(validatedStopName)
                .nameEn(nameEn)
                .nameTm(nameTm)
                .latitude(latitude)
                .longitude(longitude)
                .isActive(isActive != null ? isActive : true)
                .isMajorStop(isMajorStop != null ? isMajorStop : false)
                .cityId(cityId)
                .build();

        if (hasLocationChanged) {
            updatedStop.registerEvent(new BusStopLocationChangedEvent(this.id, latitude, longitude));
        }

        if (hasNameChanged) {
            updatedStop.registerEvent(new BusStopNameChangedEvent(this.id, stopName, nameEn, nameTm));
        }

        updatedStop.registerEvent(new BusStopUpdatedEvent(this.id, stopName));

        return updatedStop;
    }

    public BusStop updateLocationFromCoordinates(Coordinates coordinates) {
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        boolean hasLocationChanged = !this.latitude.equals(coordinates.getLatitude())
                || !this.longitude.equals(coordinates.getLongitude());

        BusStop updatedStop = this.toBuilder()
                .latitude(coordinates.getLatitude())
                .longitude(coordinates.getLongitude())
                .build();

        if (hasLocationChanged) {
            updatedStop.registerEvent(new BusStopLocationChangedEvent(
                    this.id,
                    coordinates.getLatitude(),
                    coordinates.getLongitude()
            ));
        }

        return updatedStop;
    }

    public BusStop activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder().isActive(true).build();
    }

    public BusStop deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }
        return this.toBuilder().isActive(false).build();
    }

    public BusStop markAsMajorStop() {
        if (Boolean.TRUE.equals(this.isMajorStop)) {
            return this;
        }
        return this.toBuilder().isMajorStop(true).build();
    }

    public BusStop unmarkAsMajorStop() {
        if (Boolean.FALSE.equals(this.isMajorStop)) {
            return this;
        }
        return this.toBuilder().isMajorStop(false).build();
    }


    public int getServingRoutesCount() {
        return Boolean.TRUE.equals(isMajorStop) ? 5 : 2;
    }

    public String getDisplayName(String language) {
        return switch (language != null ? language.toLowerCase() : "ru") {
            case "en" -> nameEn != null ? nameEn : stopName;
            case "tm" -> nameTm != null ? nameTm : stopName;
            default -> stopName;
        };
    }

    public boolean hasTranslation(String language) {
        return switch (language != null ? language.toLowerCase() : "ru") {
            case "en" -> nameEn != null && !nameEn.trim().isEmpty();
            case "tm" -> nameTm != null && !nameTm.trim().isEmpty();
            case "ru" -> stopName != null && !stopName.trim().isEmpty();
            default -> false;
        };
    }

    public Coordinates toCoordinates() {
        return Coordinates.of(latitude, longitude);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public boolean hasCompleteTranslations() {
        return hasTranslation("ru") && hasTranslation("en") && hasTranslation("tm");
    }


    private static String validateStopName(String stopName) {
        if (stopName == null || stopName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stop name cannot be null or empty");
        }
        return stopName.trim();
    }


    @Override
    public BusStopId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BusStop{" +
                "id=" + id +
                ", stopName='" + stopName + '\'' +
                ", stopCode=" + stopCode +
                ", isActive=" + isActive +
                ", isMajorStop=" + isMajorStop +
                ", hasLocation=" + hasLocation() +
                '}';
    }
}
