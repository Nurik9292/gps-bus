package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Builder(toBuilder = true)
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class City extends AggregateRoot<City, CityId> {

    private final CityId id;
    private final String name;
    private final String nameTm;
    private final Boolean isActive;
    private final Integer displayOrder;
    private final Double latitude;
    private final Double longitude;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static City create(String name, String nameTm, Integer displayOrder) {
        return create(name, nameTm, true, displayOrder, null, null);
    }

    public static City create(String name, String nameTm, Boolean isActive, Integer displayOrder) {
        return create(name, nameTm, isActive, displayOrder, null, null);
    }

    public static City create(String name,
                               String nameTm,
                               Boolean isActive,
                               Integer displayOrder,
                               Double latitude,
                               Double longitude) {
        String validatedName = validateNameStatic(name);
        validateCoordinates(latitude, longitude);

        return builder()
                .id(CityId.generate())
                .name(validatedName)
                .nameTm(nameTm != null ? nameTm.trim() : null)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .isActive(isActive != null ? isActive : true)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }



    public City updateCity(String name, String nameTm, Integer displayOrder) {
        return updateCity(name, nameTm, displayOrder, this.latitude, this.longitude, false);
    }

    public City updateCity(String name,
                            String nameTm,
                            Integer displayOrder,
                            Double latitude,
                            Double longitude,
                            boolean coordsProvided) {
        String newName = this.name;
        String newNameTm = this.nameTm;
        Integer newDisplayOrder = this.displayOrder;
        Double newLatitude = this.latitude;
        Double newLongitude = this.longitude;
        boolean changed = false;

        if (name != null && !name.trim().isEmpty()) {
            newName = name.trim();
            changed = true;
        }
        if (nameTm != null) {
            newNameTm = nameTm.trim();
            changed = true;
        }
        if (displayOrder != null) {
            newDisplayOrder = displayOrder;
            changed = true;
        }
        if (coordsProvided) {
            validateCoordinates(latitude, longitude);
            newLatitude = latitude;
            newLongitude = longitude;
            changed = true;
        }

        if (!changed) {
            return this;
        }

        return this.toBuilder()
                .name(newName)
                .nameTm(newNameTm)
                .displayOrder(newDisplayOrder)
                .latitude(newLatitude)
                .longitude(newLongitude)
                .build();
    }

    public City deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        return this.toBuilder()
                .isActive(false)
                .build();
    }

    public City activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }

        return this.toBuilder()
                .isActive(true)
                .build();
    }



    @Override
    public CityId getId() {
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

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null or empty");
        }
        return name.trim();
    }

    private static String validateNameStatic(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null or empty");
        }
        return name.trim();
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return;
        }
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                    "City latitude and longitude must be provided together");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                    "City latitude out of range [-90, 90]: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "City longitude out of range [-180, 180]: " + longitude);
        }
    }
}