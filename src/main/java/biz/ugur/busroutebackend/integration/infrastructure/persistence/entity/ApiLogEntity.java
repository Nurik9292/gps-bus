package biz.ugur.busroutebackend.integration.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;


@Builder
@Table("external_service_api_logs")
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class ApiLogEntity {

    @Id
    @Column("id")
    private String id;

    @Column("external_service_id")
    private String externalServiceId;

    @Column("endpoint")
    private String endpoint;

    @Column("http_method")
    private String httpMethod;

    @Column("response_status")
    private Integer responseStatus;

    @Column("response_time_ms")
    private Integer responseTimeMs;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("error_message")
    private String errorMessage;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
