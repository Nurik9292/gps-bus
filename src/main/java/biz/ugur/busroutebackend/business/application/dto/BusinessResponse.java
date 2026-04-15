package biz.ugur.busroutebackend.business.application.dto;

import biz.ugur.busroutebackend.business.domain.model.Business;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record BusinessResponse(
        @JsonProperty("id")                   String id,
        @JsonProperty("name")                 String name,
        @JsonProperty("business_type")        String businessType,
        @JsonProperty("status")               String status,
        @JsonProperty("tax_number")           String taxNumber,
        @JsonProperty("registration_number")  String registrationNumber,
        @JsonProperty("contact_person")       String contactPerson,
        @JsonProperty("contact_phone")        String contactPhone,
        @JsonProperty("contact_email")        String contactEmail,
        @JsonProperty("website_url")          String websiteUrl,
        @JsonProperty("country")              String country,
        @JsonProperty("city")                 String city,
        @JsonProperty("address_line")         String addressLine,
        @JsonProperty("logo_url")             String logoUrl,
        @JsonProperty("description")          String description,
        @JsonProperty("rejection_reason")     String rejectionReason,
        @JsonProperty("suspension_reason")    String suspensionReason,
        @JsonProperty("approved_by_admin_id") String approvedByAdminId,

        @JsonProperty("approved_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime approvedAt,

        @JsonProperty("suspended_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime suspendedAt,

        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {

    public static BusinessResponse fromDomain(Business b) {
        return new BusinessResponse(
                b.getId().getValue(),
                b.getName().getValue(),
                b.getType().name(),
                b.getStatus().name(),
                b.getTaxNumber() != null ? b.getTaxNumber().getValue() : null,
                b.getRegistrationNumber(),
                b.getContactInfo() != null ? b.getContactInfo().getContactPerson() : null,
                b.getContactInfo() != null ? b.getContactInfo().getPhone() : null,
                b.getContactInfo() != null ? b.getContactInfo().getEmail() : null,
                b.getContactInfo() != null ? b.getContactInfo().getWebsiteUrl() : null,
                b.getAddress() != null ? b.getAddress().getCountry() : null,
                b.getAddress() != null ? b.getAddress().getCity() : null,
                b.getAddress() != null ? b.getAddress().getAddressLine() : null,
                b.getLogoUrl(),
                b.getDescription(),
                b.getRejectionReason(),
                b.getSuspensionReason(),
                b.getApprovedByAdminId(),
                b.getApprovedAt(),
                b.getSuspendedAt(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
