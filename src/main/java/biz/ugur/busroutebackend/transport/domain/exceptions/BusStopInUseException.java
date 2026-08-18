package biz.ugur.busroutebackend.transport.domain.exceptions;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class BusStopInUseException extends TransportDomainException {

    public record BlockingRoute(String routeId, String routeNumber) {
    }

    private final String stopId;
    private final List<BlockingRoute> blockingRoutes;

    public BusStopInUseException(String stopId, List<BlockingRoute> blockingRoutes) {
        super("BUS_STOP_IN_USE.CONFLICT", buildMessage(blockingRoutes), Severity.WARNING);
        this.stopId = stopId;
        this.blockingRoutes = Collections.unmodifiableList(blockingRoutes);
    }

    public List<String> getRouteNumbers() {
        return blockingRoutes.stream().map(BlockingRoute::routeNumber).toList();
    }

    private static String buildMessage(List<BlockingRoute> blockingRoutes) {
        return String.format(
                "Остановка используется в активных маршрутах (%s). Сначала уберите её из этих маршрутов, затем удаляйте.",
                String.join(", ", blockingRoutes.stream().map(BlockingRoute::routeNumber).toList()));
    }
}
