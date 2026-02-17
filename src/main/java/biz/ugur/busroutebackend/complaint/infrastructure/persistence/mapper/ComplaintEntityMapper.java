package biz.ugur.busroutebackend.complaint.infrastructure.persistence.mapper;

import biz.ugur.busroutebackend.complaint.domain.model.Complaint;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintStatus;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import biz.ugur.busroutebackend.complaint.domain.valueobjects.ComplaintId;
import biz.ugur.busroutebackend.complaint.infrastructure.persistence.entity.ComplaintEntity;

public final class ComplaintEntityMapper {

    private ComplaintEntityMapper() {
    }

    public static Complaint toDomain(ComplaintEntity entity) {
        Complaint complaint = Complaint.builder()
                .id(ComplaintId.of(entity.getId()))
                .type(ComplaintType.valueOf(entity.getType()))
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(ComplaintStatus.valueOf(entity.getStatus()))
                .clientId(entity.getClientId())
                .adminComment(entity.getAdminComment())
                .build();
        complaint.setCreatedAt(entity.getCreatedAt());
        complaint.setUpdatedAt(entity.getUpdatedAt());
        complaint.setVersion(entity.getVersion());
        return complaint;
    }

    public static ComplaintEntity toEntity(Complaint complaint) {
        return ComplaintEntity.builder()
                .id(complaint.getId().getValue())
                .type(complaint.getType().name())
                .title(complaint.getTitle())
                .description(complaint.getDescription())
                .status(complaint.getStatus().name())
                .clientId(complaint.getClientId())
                .adminComment(complaint.getAdminComment())
                .createdAt(complaint.getCreatedAt())
                .updatedAt(complaint.getUpdatedAt())
                .version(complaint.getVersion())
                .build();
    }
}
