package biz.ugur.busroutebackend.client.infrastructure.persistence.entity;

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
@Table("clients")
@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class ClientEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("phone")
    private String phone;

    @Column("otp")
    private String otp;

    @Column("otp_verify")
    private Boolean otpVerify;

    @Column("platform")
    private String platform;

    @Column("status")
    private String status;

    @Column("last_activity")
    private LocalDateTime lastActivity;

    @Column("access_token")
    private String accessToken;

    @Column("refresh_token")
    private String refreshToken;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
