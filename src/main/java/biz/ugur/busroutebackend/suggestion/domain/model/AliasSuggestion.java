package biz.ugur.busroutebackend.suggestion.domain.model;

import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
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
public class AliasSuggestion extends AggregateRoot<AliasSuggestion, AliasSuggestionId> {

    private final AliasSuggestionId id;
    private final SuggestionEntityType entityType;
    private final String entityId;
    private final String suggestedAlias;
    private final String language;
    private final SuggestionStatus status;
    private final String clientId;
    private final String reviewerId;
    private final String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static AliasSuggestion create(SuggestionEntityType entityType, String entityId,
                                          String suggestedAlias, String language, String clientId) {
        if (entityType == null) {
            throw new IllegalArgumentException("Entity type cannot be null");
        }
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
        if (suggestedAlias == null || suggestedAlias.trim().isEmpty()) {
            throw new IllegalArgumentException("Suggested alias cannot be null or empty");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        return builder()
                .id(AliasSuggestionId.generate())
                .entityType(entityType)
                .entityId(entityId.trim())
                .suggestedAlias(suggestedAlias.trim())
                .language(language != null ? language : "ru")
                .status(SuggestionStatus.PENDING)
                .clientId(clientId.trim())
                .build();
    }

    public AliasSuggestion approve(String reviewerId) {
        if (reviewerId == null || reviewerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reviewer ID cannot be null or empty");
        }
        return this.toBuilder()
                .status(SuggestionStatus.APPROVED)
                .reviewerId(reviewerId.trim())
                .build();
    }

    public AliasSuggestion reject(String reviewerId, String comment) {
        if (reviewerId == null || reviewerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Reviewer ID cannot be null or empty");
        }
        return this.toBuilder()
                .status(SuggestionStatus.REJECTED)
                .reviewerId(reviewerId.trim())
                .reviewComment(comment)
                .build();
    }

    @Override
    public AliasSuggestionId getId() {
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
