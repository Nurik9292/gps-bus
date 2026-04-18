package biz.ugur.busroutebackend.business.domain.model;

import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.events.BusinessApprovedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessCreatedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessReactivatedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessRejectedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessSuspendedEvent;
import biz.ugur.busroutebackend.business.domain.events.BusinessUpdatedEvent;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessStateTransitionException;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Business extends AggregateRoot<Business, BusinessId> {

    private final BusinessId id;
    private final BusinessName name;
    private final BusinessType type;
    private final BusinessStatus status;
    private final TaxNumber taxNumber;
    private final String registrationNumber;

    private final ContactInfo contactInfo;
    private final BusinessAddress address;

    private final String logoUrl;
    private final String description;

    private final String rejectionReason;
    private final LocalDateTime approvedAt;
    private final String approvedByAdminId;
    private final LocalDateTime suspendedAt;
    private final String suspensionReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    // -------------------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------------------

    public static Business create(BusinessName name,
                                   BusinessType type,
                                   ContactInfo contactInfo,
                                   BusinessAddress address,
                                   TaxNumber taxNumber,
                                   String registrationNumber,
                                   String logoUrl,
                                   String description) {
        if (name == null) throw new BusinessValidationException("name", "must not be null");
        if (type == null) throw new BusinessValidationException("businessType", "must not be null");
        if (contactInfo == null) throw new BusinessValidationException("contactInfo", "must not be null");

        Business business = builder()
                .id(BusinessId.generate())
                .name(name)
                .type(type)
                .status(BusinessStatus.PENDING)
                .taxNumber(taxNumber)
                .registrationNumber(trimOrNull(registrationNumber))
                .contactInfo(contactInfo)
                .address(address != null ? address : BusinessAddress.empty())
                .logoUrl(trimOrNull(logoUrl))
                .description(trimOrNull(description))
                .version(0L)
                .build();

        business.registerEvent(new BusinessCreatedEvent(
                business.id.getValue(),
                business.name.getValue(),
                business.type,
                business.contactInfo.getPhone()
        ));

        return business;
    }

    public static Business restore(BusinessId id,
                                    BusinessName name,
                                    BusinessType type,
                                    BusinessStatus status,
                                    TaxNumber taxNumber,
                                    String registrationNumber,
                                    ContactInfo contactInfo,
                                    BusinessAddress address,
                                    String logoUrl,
                                    String description,
                                    String rejectionReason,
                                    LocalDateTime approvedAt,
                                    String approvedByAdminId,
                                    LocalDateTime suspendedAt,
                                    String suspensionReason,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt,
                                    Long version) {
        return builder()
                .id(id)
                .name(name)
                .type(type)
                .status(status)
                .taxNumber(taxNumber)
                .registrationNumber(registrationNumber)
                .contactInfo(contactInfo)
                .address(address)
                .logoUrl(logoUrl)
                .description(description)
                .rejectionReason(rejectionReason)
                .approvedAt(approvedAt)
                .approvedByAdminId(approvedByAdminId)
                .suspendedAt(suspendedAt)
                .suspensionReason(suspensionReason)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    // -------------------------------------------------------------------------------------
    // Behaviour — status transitions
    // -------------------------------------------------------------------------------------

    public Business approve(String adminId) {
        guardTransition(BusinessStatus.APPROVED);
        if (adminId == null || adminId.isBlank()) {
            throw new BusinessValidationException("approvedByAdminId", "must not be blank");
        }
        Business approved = this.toBuilder()
                .status(BusinessStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .approvedByAdminId(adminId.trim())
                .rejectionReason(null)
                .suspendedAt(null)
                .suspensionReason(null)
                .build();
        approved.registerEvent(new BusinessApprovedEvent(this.id.getValue(), adminId.trim()));
        return approved;
    }

    public Business reject(String reason) {
        guardTransition(BusinessStatus.REJECTED);
        String cleanReason = requireReason(reason, "rejectionReason");
        Business rejected = this.toBuilder()
                .status(BusinessStatus.REJECTED)
                .rejectionReason(cleanReason)
                .build();
        rejected.registerEvent(new BusinessRejectedEvent(this.id.getValue(), cleanReason));
        return rejected;
    }

    public Business suspend(String reason) {
        guardTransition(BusinessStatus.SUSPENDED);
        String cleanReason = requireReason(reason, "suspensionReason");
        Business suspended = this.toBuilder()
                .status(BusinessStatus.SUSPENDED)
                .suspendedAt(LocalDateTime.now())
                .suspensionReason(cleanReason)
                .build();
        suspended.registerEvent(new BusinessSuspendedEvent(this.id.getValue(), cleanReason));
        return suspended;
    }

    public Business reactivate() {
        guardTransition(BusinessStatus.APPROVED);
        if (this.status != BusinessStatus.SUSPENDED) {
            throw new BusinessStateTransitionException(this.status, BusinessStatus.APPROVED);
        }
        Business reactivated = this.toBuilder()
                .status(BusinessStatus.APPROVED)
                .suspendedAt(null)
                .suspensionReason(null)
                .build();
        reactivated.registerEvent(new BusinessReactivatedEvent(this.id.getValue()));
        return reactivated;
    }

    // -------------------------------------------------------------------------------------
    // Behaviour — profile update
    // -------------------------------------------------------------------------------------

    public Business updateProfile(BusinessName name,
                                   BusinessType type,
                                   ContactInfo contactInfo,
                                   BusinessAddress address,
                                   TaxNumber taxNumber,
                                   String registrationNumber,
                                   String logoUrl,
                                   String description) {
        if (this.status == BusinessStatus.REJECTED) {
            throw new BusinessStateTransitionException(this.status, this.status);
        }

        Map<String, Object> changes = new HashMap<>();

        BusinessName newName = name != null ? name : this.name;
        if (!newName.equals(this.name)) changes.put("name", newName.getValue());

        BusinessType newType = type != null ? type : this.type;
        if (newType != this.type) changes.put("type", newType.name());

        ContactInfo newContact = contactInfo != null ? contactInfo : this.contactInfo;
        if (!newContact.equals(this.contactInfo)) changes.put("contactInfo", true);

        BusinessAddress newAddress = address != null ? address : this.address;
        if (!newAddress.equals(this.address)) changes.put("address", true);

        TaxNumber newTax = taxNumber != null ? taxNumber : this.taxNumber;
        if (!Objects.equals(newTax, this.taxNumber)) changes.put("taxNumber", true);

        String newRegistration = trimOrNull(registrationNumber);
        if (!Objects.equals(newRegistration, this.registrationNumber)) {
            changes.put("registrationNumber", newRegistration);
        }

        String newLogo = trimOrNull(logoUrl);
        if (!Objects.equals(newLogo, this.logoUrl)) changes.put("logoUrl", newLogo);

        String newDescription = trimOrNull(description);
        if (!Objects.equals(newDescription, this.description)) {
            changes.put("description", newDescription);
        }

        Business updated = this.toBuilder()
                .name(newName)
                .type(newType)
                .contactInfo(newContact)
                .address(newAddress)
                .taxNumber(newTax)
                .registrationNumber(newRegistration)
                .logoUrl(newLogo)
                .description(newDescription)
                .build();

        if (!changes.isEmpty()) {
            updated.registerEvent(new BusinessUpdatedEvent(this.id.getValue(), changes));
        }

        return updated;
    }

    // -------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------

    private void guardTransition(BusinessStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessStateTransitionException(this.status, target);
        }
    }

    private static String requireReason(String reason, String field) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessValidationException(field, "must not be blank");
        }
        return reason.trim();
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    // -------------------------------------------------------------------------------------
    // AggregateRoot contract
    // -------------------------------------------------------------------------------------

    @Override public BusinessId getId() { return id; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
