package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
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

    @Column("has_shelter")
    private Boolean hasShelter;

    // Constructor для создания нового BusStop
    public BusStop(String stopName, String stopCode, BigDecimal latitude, BigDecimal longitude) {
        this.id = BusStopId.generate();
        this.stopName = validateStopName(stopName);
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = true;
        this.isMajorStop = false;
        this.hasShelter = false;
    }

    // Constructor для R2DBC mapping
    public BusStop(BusStopId id, String stopName, String stopCode, BigDecimal latitude,
                   BigDecimal longitude, Boolean isActive, Boolean isMajorStop, Boolean hasShelter) {
        this.id = id;
        this.stopName = stopName;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive;
        this.isMajorStop = isMajorStop;
        this.hasShelter = hasShelter;
    }

    public int getServingRoutesCount() {
        // Заглушка - в реальности нужен подсчет через репозиторий
        return isMajorStop ? 5 : 2;
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