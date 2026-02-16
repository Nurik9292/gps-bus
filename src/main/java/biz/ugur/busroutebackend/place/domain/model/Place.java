package biz.ugur.busroutebackend.place.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class Place extends AggregateRoot<Place, PlaceId> {

    private final PlaceId id;
    private final String name;
    private final String nameEn;
    private final String nameTm;
    private final String description;
    private final String address;
    private final PlaceCategory category;
    private final String cityId;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static Place create(String name, String nameEn, String nameTm,
                               String description, String address,
                               PlaceCategory category, String cityId,
                               BigDecimal latitude, BigDecimal longitude,
                               Boolean isActive) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Place name cannot be null or empty");
        }
        if (category == null) {
            throw new IllegalArgumentException("Place category cannot be null");
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }

        return builder()
                .id(PlaceId.generate())
                .name(name.trim())
                .nameEn(nameEn != null ? nameEn.trim() : null)
                .nameTm(nameTm != null ? nameTm.trim() : null)
                .description(description)
                .address(address)
                .category(category)
                .cityId(cityId)
                .latitude(latitude)
                .longitude(longitude)
                .isActive(isActive != null ? isActive : true)
                .build();
    }

    public Place updateInfo(String name, String nameEn, String nameTm,
                            String description, String address,
                            PlaceCategory category, String cityId,
                            BigDecimal latitude, BigDecimal longitude) {
        return this.toBuilder()
                .name(name != null ? name.trim() : this.name)
                .nameEn(nameEn != null ? nameEn.trim() : this.nameEn)
                .nameTm(nameTm != null ? nameTm.trim() : this.nameTm)
                .description(description != null ? description : this.description)
                .address(address != null ? address : this.address)
                .category(category != null ? category : this.category)
                .cityId(cityId != null ? cityId : this.cityId)
                .latitude(latitude != null ? latitude : this.latitude)
                .longitude(longitude != null ? longitude : this.longitude)
                .build();
    }

    public Coordinates toCoordinates() {
        return Coordinates.of(latitude, longitude);
    }

    @Override
    public PlaceId getId() {
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
}
