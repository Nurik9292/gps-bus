package biz.ugur.busroutebackend.complaint.application.dto;

public record UpdateComplaintStatusInput(
        String complaintId,
        String adminComment
) {
}
