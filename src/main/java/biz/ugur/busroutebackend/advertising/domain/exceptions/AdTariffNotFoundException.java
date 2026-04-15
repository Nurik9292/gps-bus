package biz.ugur.busroutebackend.advertising.domain.exceptions;

public class AdTariffNotFoundException extends AdvertisingDomainException {

    public AdTariffNotFoundException(String tariffId) {
        super("TARIFF_NOT_FOUND", "Ad tariff not found: " + tariffId, Severity.WARNING);
    }
}
