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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static City create(String name, String nameTm, Integer displayOrder) {
        String validatedName = validateNameStatic(name);

        return builder()
                .id(CityId.generate())
                .name(validatedName)
                .nameTm(nameTm != null ? nameTm.trim() : null)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .isActive(true)
                .build();
    }



    public City updateCity(String name, String nameTm, Integer displayOrder) {
        String newName = this.name;
        String newNameTm = this.nameTm;
        Integer newDisplayOrder = this.displayOrder;
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

        if (!changed) {
            return this;
        }

        return this.toBuilder()
                .name(newName)
                .nameTm(newNameTm)
                .displayOrder(newDisplayOrder)
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
}