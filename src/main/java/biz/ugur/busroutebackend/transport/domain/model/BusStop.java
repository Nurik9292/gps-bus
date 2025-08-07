package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.event.*;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Table("bus_stops")
public class BusStop extends AggregateRoot<BusStop, BusStopId> {

    @Id
    @Column("id")
    private BusStopId id;

    @Column("stop_name")
    private String stopName;

    @Column("name_en")
    private String nameEn;

    @Column("name_tm")
    private String nameTm;

    @Column("city_id")
    private String cityId;

    @Column("stop_code")
    private String stopCode;

    @Column("latitude")
    private BigDecimal latitude;

    @Column("longitude")
    private BigDecimal longitude;

    @Column("is_active")
    private Boolean isActive;

    @Column("is_major_stop")
    private Boolean isMajorStop;


    public BusStop(String stopName,
                   String nameEn,
                   String nameTm,
                   String stopCode,
                   BigDecimal latitude,
                   BigDecimal longitude,
                   Boolean isMajorStop,
                   String cityId
    ) {
        this.id = BusStopId.generate();
        this.stopName = validateStopName(stopName);
        this.nameEn = nameEn;
        this.nameTm = nameTm;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = true;
        this.isMajorStop = isMajorStop;
        this.cityId = cityId;

        generateCreationEvents();
    }
    public BusStop(BusStopId id,
                   String stopName,
                   String nameEn,
                   String nameTm,
                   String stopCode,
                   BigDecimal latitude,
                   BigDecimal longitude,
                   Boolean isActive,
                   Boolean isMajorStop,
                   String cityId) {
        this.id = id;
        this.stopName = stopName;
        this.nameEn = nameEn;
        this.nameTm = nameTm;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive;
        this.isMajorStop = isMajorStop;
        this.cityId = cityId;
    }

    public BusStop(String stopName, String id, BigDecimal latitude, BigDecimal longitude) {
        this.stopName = validateStopName(stopName);
        this.id = new BusStopId(id);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public BusStop(
            BusStopId id,
            String stopName,
            String stopCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean isActive,
            Boolean isMajorStop) {
        this.id = id;
        this.stopName = stopName;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive;
        this.isMajorStop = isMajorStop;
    }

    private void generateCreationEvents() {
        registerEvent(new BusStopCreatedEvent(
                this.id,
                this.stopName,
                this.nameEn,
                this.nameTm,
                this.stopCode,
                this.latitude,
                this.longitude,
                this.isMajorStop
        ));

        registerEvent(new BusStopRegisteredEvent(
                this.id,
                this.stopName,
                this.stopCode,
                "admin" // TODO: получать из контекста безопасности
        ));
    }

    public int getServingRoutesCount() {

        return isMajorStop ? 5 : 2;
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

    public void updateInfo(String stopName, String nameEn, String nameTm, String stopCode,
                           BigDecimal latitude, BigDecimal longitude, Boolean isActive, Boolean isMajorStop) {
        boolean hasLocationChanged = !this.latitude.equals(latitude) || !this.longitude.equals(longitude);
        boolean hasNameChanged = !this.stopName.equals(stopName);

        this.stopName = validateStopName(stopName);
        this.nameEn = nameEn;
        this.nameTm = nameTm;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive != null ? isActive : true;
        this.isMajorStop = isMajorStop != null ? isMajorStop : false;

        if (hasLocationChanged) {
            registerEvent(new BusStopLocationChangedEvent(this.id, latitude, longitude));
        }

        if (hasNameChanged) {
            registerEvent(new BusStopNameChangedEvent(this.id, stopName, nameEn, nameTm));
        }

        registerEvent(new BusStopUpdatedEvent(this.id, stopName));
    }


    @Override
    public BusStopId getId() {
        return id;
    }

    private String validateStopName(String stopName) {
        if (stopName == null || stopName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stop name cannot be null or empty");
        }
        return stopName.trim();
    }
}