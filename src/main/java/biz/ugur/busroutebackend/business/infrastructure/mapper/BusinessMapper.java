package biz.ugur.busroutebackend.business.infrastructure.mapper;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
import biz.ugur.busroutebackend.business.infrastructure.persistence.entity.BusinessEntity;

public final class BusinessMapper {

    private BusinessMapper() {}

    public static Business toDomain(BusinessEntity entity) {
        return Business.restore(
                BusinessId.of(entity.getId()),
                BusinessName.of(entity.getName()),
                BusinessType.valueOf(entity.getBusinessType()),
                BusinessStatus.valueOf(entity.getStatus()),
                entity.getTaxNumber() != null ? TaxNumber.of(entity.getTaxNumber()) : null,
                entity.getRegistrationNumber(),
                ContactInfo.of(entity.getContactPerson(),
                                entity.getContactPhone(),
                                entity.getContactEmail(),
                                entity.getWebsiteUrl()),
                BusinessAddress.of(entity.getCountry(), entity.getCity(), entity.getAddressLine()),
                entity.getLogoUrl(),
                entity.getDescription(),
                entity.getRejectionReason(),
                entity.getApprovedAt(),
                entity.getApprovedByAdminId(),
                entity.getSuspendedAt(),
                entity.getSuspensionReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static BusinessEntity toEntity(Business business) {
        ContactInfo contact = business.getContactInfo();
        BusinessAddress address = business.getAddress();

        return BusinessEntity.builder()
                .id(business.getId().getValue())
                .name(business.getName().getValue())
                .businessType(business.getType().name())
                .status(business.getStatus().name())
                .taxNumber(business.getTaxNumber() != null ? business.getTaxNumber().getValue() : null)
                .registrationNumber(business.getRegistrationNumber())
                .contactPerson(contact != null ? contact.getContactPerson() : null)
                .contactPhone(contact != null ? contact.getPhone() : null)
                .contactEmail(contact != null ? contact.getEmail() : null)
                .websiteUrl(contact != null ? contact.getWebsiteUrl() : null)
                .country(address != null ? address.getCountry() : null)
                .city(address != null ? address.getCity() : null)
                .addressLine(address != null ? address.getAddressLine() : null)
                .logoUrl(business.getLogoUrl())
                .description(business.getDescription())
                .rejectionReason(business.getRejectionReason())
                .approvedAt(business.getApprovedAt())
                .approvedByAdminId(business.getApprovedByAdminId())
                .suspendedAt(business.getSuspendedAt())
                .suspensionReason(business.getSuspensionReason())
                .createdAt(business.getCreatedAt())
                .updatedAt(business.getUpdatedAt())
                .version(business.getVersion())
                .build();
    }
}
