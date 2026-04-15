package biz.ugur.busroutebackend.business.domain.exceptions;

public class BusinessNotFoundException extends BusinessDomainException {

    public BusinessNotFoundException(String businessId) {
        super("NOT_FOUND", "Business not found: " + businessId, Severity.WARNING);
    }
}
