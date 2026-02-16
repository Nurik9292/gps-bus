package biz.ugur.busroutebackend.suggestion.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Table("alias_suggestions")
@Getter
@ToString
@EqualsAndHashCode
public class AliasSuggestionEntity {

    @Id
    @Column("id")
    private String id;

    @Column("entity_type")
    private String entityType;

    @Column("entity_id")
    private String entityId;

    @Column("suggested_alias")
    private String suggestedAlias;

    @Column("language")
    private String language;

    @Column("status")
    private String status;

    @Column("client_id")
    private String clientId;

    @Column("reviewer_id")
    private String reviewerId;

    @Column("review_comment")
    private String reviewComment;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
