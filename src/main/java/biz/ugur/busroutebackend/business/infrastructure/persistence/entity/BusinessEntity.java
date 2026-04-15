package biz.ugur.busroutebackend.business.infrastructure.persistence.entity;

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
@Getter
@Table("businesses")
@EqualsAndHashCode(callSuper = false)
public class BusinessEntity {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("business_type")
    private String businessType;

    @Column("status")
    private String status;

    @Column("tax_number")
    private String taxNumber;

    @Column("registration_number")
    private String registrationNumber;

    @Column("contact_person")
    private String contactPerson;

    @Column("contact_phone")
    private String contactPhone;

    @Column("contact_email")
    private String contactEmail;

    @Column("website_url")
    private String websiteUrl;

    @Column("country")
    private String country;

    @Column("city")
    private String city;

    @Column("address_line")
    private String addressLine;

    @Column("logo_url")
    private String logoUrl;

    @Column("description")
    private String description;

    @Column("rejection_reason")
    private String rejectionReason;

    @Column("approved_at")
    private LocalDateTime approvedAt;

    @Column("approved_by_admin_id")
    private String approvedByAdminId;

    @Column("suspended_at")
    private LocalDateTime suspendedAt;

    @Column("suspension_reason")
    private String suspensionReason;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
