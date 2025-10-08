package biz.ugur.busroutebackend.transport.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;


@Getter
public class RealTimeDataException extends TransportDomainException {

    @Getter
    public enum DataErrorType {
        DATA_UNAVAILABLE("Real-time data is currently unavailable"),
        STALE_DATA("Real-time data is stale or outdated"),
        EXTERNAL_API_ERROR("Error communicating with external data provider"),
        PARSING_ERROR("Error parsing real-time data"),
        TIMEOUT("Request to real-time data provider timed out"),
        RATE_LIMIT_EXCEEDED("Rate limit exceeded for real-time data API");

        private final String defaultMessage;

        DataErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
    }

    private final DataErrorType dataErrorType;
    private final String vehicleId;
    private final String routeCode;

    public RealTimeDataException(DataErrorType dataErrorType) {
        super("REAL_TIME_DATA_ERROR", dataErrorType.getDefaultMessage(), Severity.WARNING);
        this.dataErrorType = dataErrorType;
        this.vehicleId = null;
        this.routeCode = null;
    }

    public RealTimeDataException(DataErrorType dataErrorType, CorrelationId correlationId) {
        super("REAL_TIME_DATA_ERROR", dataErrorType.getDefaultMessage(), Severity.WARNING, correlationId);
        this.dataErrorType = dataErrorType;
        this.vehicleId = null;
        this.routeCode = null;
    }

    public RealTimeDataException(DataErrorType dataErrorType, String vehicleId, String routeCode, CorrelationId correlationId) {
        super("REAL_TIME_DATA_ERROR",
                String.format("%s (Vehicle: %s, Route: %s)", dataErrorType.getDefaultMessage(), vehicleId, routeCode),
                Severity.ERROR,
                correlationId);
        this.dataErrorType = dataErrorType;
        this.vehicleId = vehicleId;
        this.routeCode = routeCode;
    }

    public static RealTimeDataException dataUnavailable() {
        return new RealTimeDataException(DataErrorType.DATA_UNAVAILABLE);
    }

    public static RealTimeDataException dataUnavailable(CorrelationId correlationId) {
        return new RealTimeDataException(DataErrorType.DATA_UNAVAILABLE, correlationId);
    }

    public static RealTimeDataException externalApiError(CorrelationId correlationId) {
        return new RealTimeDataException(DataErrorType.EXTERNAL_API_ERROR, correlationId);
    }

    public static RealTimeDataException timeout(CorrelationId correlationId) {
        return new RealTimeDataException(DataErrorType.TIMEOUT, correlationId);
    }

    public boolean isRetryable() {
        return dataErrorType == DataErrorType.TIMEOUT ||
               dataErrorType == DataErrorType.EXTERNAL_API_ERROR;
    }
}
