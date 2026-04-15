package biz.ugur.busroutebackend.business.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.infrastructure.mapper.BusinessMapper;
import biz.ugur.busroutebackend.business.infrastructure.persistence.entity.BusinessEntity;
import biz.ugur.busroutebackend.shared.infrastructure.persistence.BaseR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class BusinessBaseRepository extends BaseR2dbcRepository<Business, BusinessId> {

    protected BusinessBaseRepository(DatabaseClient databaseClient) {
        super(databaseClient, "businesses", Business.class);
    }

    @Override
    protected String convertIdToDatabase(BusinessId id) {
        return id.getValue();
    }

    @Override
    protected BiFunction<Row, RowMetadata, Business> getRowMapper() {
        return this::mapRow;
    }

    @Override
    protected Map<String, Object> mapEntityToColumns(Business business) {
        BusinessEntity entity = BusinessMapper.toEntity(business);
        Map<String, Object> columns = new HashMap<>();
        columns.put("id", entity.getId());
        columns.put("name", entity.getName());
        columns.put("business_type", entity.getBusinessType());
        columns.put("status", entity.getStatus());
        columns.put("tax_number", entity.getTaxNumber());
        columns.put("registration_number", entity.getRegistrationNumber());
        columns.put("contact_person", entity.getContactPerson());
        columns.put("contact_phone", entity.getContactPhone());
        columns.put("contact_email", entity.getContactEmail());
        columns.put("website_url", entity.getWebsiteUrl());
        columns.put("country", entity.getCountry());
        columns.put("city", entity.getCity());
        columns.put("address_line", entity.getAddressLine());
        columns.put("logo_url", entity.getLogoUrl());
        columns.put("description", entity.getDescription());
        columns.put("rejection_reason", entity.getRejectionReason());
        columns.put("approved_at", entity.getApprovedAt());
        columns.put("approved_by_admin_id", entity.getApprovedByAdminId());
        columns.put("suspended_at", entity.getSuspendedAt());
        columns.put("suspension_reason", entity.getSuspensionReason());
        columns.put("created_at", entity.getCreatedAt());
        columns.put("updated_at", entity.getUpdatedAt());
        columns.put("version", entity.getVersion());
        return columns;
    }

    private Business mapRow(Row row, RowMetadata metadata) {
        return BusinessMapper.toDomain(BusinessEntity.builder()
                .id(row.get("id", String.class))
                .name(row.get("name", String.class))
                .businessType(row.get("business_type", String.class))
                .status(row.get("status", String.class))
                .taxNumber(row.get("tax_number", String.class))
                .registrationNumber(row.get("registration_number", String.class))
                .contactPerson(row.get("contact_person", String.class))
                .contactPhone(row.get("contact_phone", String.class))
                .contactEmail(row.get("contact_email", String.class))
                .websiteUrl(row.get("website_url", String.class))
                .country(row.get("country", String.class))
                .city(row.get("city", String.class))
                .addressLine(row.get("address_line", String.class))
                .logoUrl(row.get("logo_url", String.class))
                .description(row.get("description", String.class))
                .rejectionReason(row.get("rejection_reason", String.class))
                .approvedAt(row.get("approved_at", LocalDateTime.class))
                .approvedByAdminId(row.get("approved_by_admin_id", String.class))
                .suspendedAt(row.get("suspended_at", LocalDateTime.class))
                .suspensionReason(row.get("suspension_reason", String.class))
                .createdAt(row.get("created_at", LocalDateTime.class))
                .updatedAt(row.get("updated_at", LocalDateTime.class))
                .version(row.get("version", Long.class))
                .build());
    }
}
