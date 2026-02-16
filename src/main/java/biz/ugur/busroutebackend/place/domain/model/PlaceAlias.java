package biz.ugur.busroutebackend.place.domain.model;

import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
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
public class PlaceAlias extends AggregateRoot<PlaceAlias, PlaceAliasId> {

    private final PlaceAliasId id;
    private final String placeId;
    private final String alias;
    private final String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static PlaceAlias create(String placeId, String alias, String language) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Alias cannot be null or empty");
        }
        return builder()
                .id(PlaceAliasId.generate())
                .placeId(placeId)
                .alias(alias.trim())
                .language(language != null ? language : "ru")
                .build();
    }

    public PlaceAlias updateAlias(String alias, String language) {
        return this.toBuilder()
                .alias(alias != null ? alias.trim() : this.alias)
                .language(language != null ? language : this.language)
                .build();
    }

    @Override
    public PlaceAliasId getId() {
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
