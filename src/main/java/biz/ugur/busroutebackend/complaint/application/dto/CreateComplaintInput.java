package biz.ugur.busroutebackend.complaint.application.dto;

import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;

public record CreateComplaintInput(
        ComplaintType type,
        String title,
        String description,
        String clientId
) {
}
