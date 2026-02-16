package biz.ugur.busroutebackend.place.domain.model;

import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
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
public class StreetAlias extends AggregateRoot<StreetAlias, StreetAliasId> {

    private final StreetAliasId id;
    private final String streetId;
    private final String alias;
    private final String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static StreetAlias create(String streetId, String alias, String language) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Alias cannot be null or empty");
        }
        return builder()
                .id(StreetAliasId.generate())
                .streetId(streetId)
                .alias(alias.trim())
                .language(language != null ? language : "ru")
                .build();
    }

    public StreetAlias updateAlias(String alias, String language) {
        return this.toBuilder()
                .alias(alias != null ? alias.trim() : this.alias)
                .language(language != null ? language : this.language)
                .build();
    }

    @Override
    public StreetAliasId getId() {
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
