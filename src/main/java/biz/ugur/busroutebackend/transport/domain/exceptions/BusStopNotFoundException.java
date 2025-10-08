package biz.ugur.busroutebackend.transport.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class BusStopNotFoundException extends TransportDomainException {

    private final String identifier;
    private final String identifierType;

    public BusStopNotFoundException(String identifier, String identifierType) {
        super("BUS_STOP_NOT_FOUND", String.format("Bus stop not found with %s: %s", identifierType, identifier), Severity.WARNING);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public BusStopNotFoundException(String identifier, String identifierType, CorrelationId correlationId) {
        super("BUS_STOP_NOT_FOUND", String.format("Bus stop not found with %s: %s", identifierType, identifier), Severity.WARNING, correlationId);
        this.identifier = identifier;
        this.identifierType = identifierType;
    }

    public static BusStopNotFoundException byId(Long id) {
        return new BusStopNotFoundException(id.toString(), "id");
    }

    public static BusStopNotFoundException byId(Long id, CorrelationId correlationId) {
        return new BusStopNotFoundException(id.toString(), "id", correlationId);
    }

    public static BusStopNotFoundException byStopCode(String stopCode) {
        return new BusStopNotFoundException(stopCode, "stopCode");
    }

    public static BusStopNotFoundException byStopCode(String stopCode, CorrelationId correlationId) {
        return new BusStopNotFoundException(stopCode, "stopCode", correlationId);
    }

    public static BusStopNotFoundException byName(String name) {
        return new BusStopNotFoundException(name, "name");
    }

    public static BusStopNotFoundException byName(String name, CorrelationId correlationId) {
        return new BusStopNotFoundException(name, "name", correlationId);
    }
}
