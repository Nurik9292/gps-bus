package biz.ugur.busroutebackend.integration.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;


@Builder
@Table("external_services")
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class ExternalServiceEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("api_token")
    private String apiToken;

    @Column("is_active")
    private Boolean isActive;

    @Column("allowed_endpoints")
    private List<String> allowedEndpoints;

    @Column("rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @Column("last_used_at")
    private LocalDateTime lastUsedAt;

    @Column("created_by_admin_id")
    private String createdByAdminId;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column("version")
    private Long version;
}
