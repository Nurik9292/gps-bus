package biz.ugur.busroutebackend.notification.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Table("notifications")
@Getter
@EqualsAndHashCode(callSuper = false)
public class NotificationEntity {
    @Id
    @Column("id")
    private String id;

    @Column("title")
    private String title;

    @Column("content")
    private String content;

    @Column("is_active")
    private Boolean isActive;

    @Column("display_order")
    private Integer displayOrder;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    private Long version;
}
