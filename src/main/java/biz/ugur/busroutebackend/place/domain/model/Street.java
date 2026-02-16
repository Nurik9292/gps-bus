package biz.ugur.busroutebackend.place.domain.model;

import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
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
public class Street extends AggregateRoot<Street, StreetId> {

    private final StreetId id;
    private final String name;
    private final String nameEn;
    private final String nameTm;
    private final String cityId;
    private final Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static Street create(String name, String nameEn, String nameTm,
                                String cityId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Street name cannot be null or empty");
        }
        return builder()
                .id(StreetId.generate())
                .name(name.trim())
                .nameEn(nameEn != null ? nameEn.trim() : null)
                .nameTm(nameTm != null ? nameTm.trim() : null)
                .cityId(cityId)
                .isActive(true)
                .build();
    }

    public Street updateInfo(String name, String nameEn, String nameTm, String cityId) {
        return this.toBuilder()
                .name(name != null ? name.trim() : this.name)
                .nameEn(nameEn != null ? nameEn.trim() : this.nameEn)
                .nameTm(nameTm != null ? nameTm.trim() : this.nameTm)
                .cityId(cityId != null ? cityId : this.cityId)
                .build();
    }

    @Override
    public StreetId getId() {
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
