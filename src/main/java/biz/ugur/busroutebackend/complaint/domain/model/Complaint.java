package biz.ugur.busroutebackend.complaint.domain.model;

import biz.ugur.busroutebackend.complaint.domain.valueobjects.ComplaintId;
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
public class Complaint extends AggregateRoot<Complaint, ComplaintId> {

    private final ComplaintId id;
    private final ComplaintType type;
    private final String title;
    private final String description;
    private final ComplaintStatus status;
    private final String clientId;
    private final String adminComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static Complaint create(ComplaintType type, String title, String description, String clientId) {
        if (type == null) {
            throw new IllegalArgumentException("Complaint type cannot be null");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint title cannot be null or empty");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Complaint description cannot be null or empty");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }

        return builder()
                .id(ComplaintId.generate())
                .type(type)
                .title(title.trim())
                .description(description.trim())
                .status(ComplaintStatus.NEW)
                .clientId(clientId.trim())
                .build();
    }

    public Complaint markInProgress() {
        return this.toBuilder()
                .status(ComplaintStatus.IN_PROGRESS)
                .build();
    }

    public Complaint resolve(String adminComment) {
        return this.toBuilder()
                .status(ComplaintStatus.RESOLVED)
                .adminComment(adminComment)
                .build();
    }

    public Complaint close(String adminComment) {
        return this.toBuilder()
                .status(ComplaintStatus.CLOSED)
                .adminComment(adminComment)
                .build();
    }

    @Override
    public ComplaintId getId() {
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
