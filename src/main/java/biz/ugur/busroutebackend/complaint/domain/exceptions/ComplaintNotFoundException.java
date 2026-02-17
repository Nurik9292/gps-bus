package biz.ugur.busroutebackend.complaint.domain.exceptions;

public class ComplaintNotFoundException extends ComplaintDomainException {

    public ComplaintNotFoundException(String complaintId) {
        super("complaint.not_found", "Complaint not found with ID: " + complaintId);
    }
}
