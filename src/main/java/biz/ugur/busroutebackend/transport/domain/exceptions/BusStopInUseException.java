package biz.ugur.busroutebackend.transport.domain.exceptions;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class BusStopInUseException extends TransportDomainException {

    private final String stopId;
    private final List<String> routeNumbers;

    public BusStopInUseException(String stopId, List<String> routeNumbers) {
        super("BUS_STOP_IN_USE.CONFLICT", buildMessage(stopId, routeNumbers), Severity.WARNING);
        this.stopId = stopId;
        this.routeNumbers = Collections.unmodifiableList(routeNumbers);
    }

    private static String buildMessage(String stopId, List<String> routeNumbers) {
        return String.format(
                "Остановка используется в активных маршрутах (%s). Сначала уберите её из этих маршрутов, затем удаляйте.",
                String.join(", ", routeNumbers));
    }
}
