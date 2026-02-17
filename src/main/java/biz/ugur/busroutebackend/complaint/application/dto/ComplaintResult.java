package biz.ugur.busroutebackend.complaint.application.dto;

import biz.ugur.busroutebackend.complaint.domain.model.Complaint;

import java.time.LocalDateTime;

public record ComplaintResult(
        String id,
        String type,
        String title,
        String description,
        String status,
        String clientId,
        String adminComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ComplaintResult fromDomain(Complaint complaint) {
        return new ComplaintResult(
                complaint.getId().getValue(),
                complaint.getType().name(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getStatus().name(),
                complaint.getClientId(),
                complaint.getAdminComment(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt()
        );
    }
}
