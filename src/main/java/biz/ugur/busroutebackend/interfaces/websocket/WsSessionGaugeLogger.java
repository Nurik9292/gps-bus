package biz.ugur.busroutebackend.interfaces.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WsSessionGaugeLogger {

    private static final long ONE_MINUTE_MS = 60_000L;

    private final WsOutboundStreamBuilder streamBuilder;

    public WsSessionGaugeLogger(WsOutboundStreamBuilder streamBuilder) {
        this.streamBuilder = streamBuilder;
    }

    @Scheduled(initialDelay = ONE_MINUTE_MS, fixedRate = ONE_MINUTE_MS)
    public void logActiveSessions() {
        log.info("[WS] активных соединений: {}", streamBuilder.activeSessions());
    }
}
