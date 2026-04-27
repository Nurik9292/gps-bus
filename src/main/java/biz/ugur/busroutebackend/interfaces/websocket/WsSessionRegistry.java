package biz.ugur.busroutebackend.interfaces.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WsSessionRegistry {

    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(5);
    private static final long CLEANUP_INTERVAL_MS = 30_000;

    private final Map<String, SessionConfig> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    private final AtomicLong totalExpiredSessions = new AtomicLong(0);

    public String register(WebSocketSession session) {
        String sessionId = "ws-" + sessionCounter.incrementAndGet() + "-" + System.currentTimeMillis();
        SessionConfig config = parseSessionConfig(session);
        config.setClientIp(getClientIp(session));
        activeSessions.put(sessionId, config);
        return sessionId;
    }

    public Optional<SessionConfig> get(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    public SessionConfig remove(String sessionId) {
        return activeSessions.remove(sessionId);
    }

    public int activeCount() {
        return activeSessions.size();
    }

    public Map<String, Long> subscriptionTypeCounts() {
        return activeSessions.values().stream()
                .collect(Collectors.groupingBy(
                        SessionConfig::getSubscriptionType,
                        Collectors.counting()));
    }

    public long totalExpired() {
        return totalExpiredSessions.get();
    }

    public Map<String, SessionConfig> snapshotForLog() {
        return Collections.unmodifiableMap(new HashMap<>(activeSessions));
    }

    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupExpired() {
        if (activeSessions.isEmpty()) {
            return;
        }

        int beforeSize = activeSessions.size();
        List<String> expiredSessionIds = new ArrayList<>();

        activeSessions.forEach((sessionId, config) -> {
            if (config.isExpired(SESSION_TIMEOUT)) {
                expiredSessionIds.add(sessionId);
            }
        });

        if (expiredSessionIds.isEmpty()) {
            log.debug("Session cleanup: no expired sessions found (active: {})", beforeSize);
            return;
        }

        for (String sessionId : expiredSessionIds) {
            SessionConfig config = activeSessions.remove(sessionId);
            if (config != null) {
                totalExpiredSessions.incrementAndGet();
                log.info("WEBSOCKET_SESSION_EXPIRED: Removed stale session {} from {} " +
                                "(inactive for {}, subscription: {})",
                        sessionId,
                        config.getClientIp(),
                        formatDuration(config.getInactiveDuration()),
                        config.getSubscriptionType());
            }
        }

        log.warn("WEBSOCKET_SESSION_CLEANUP: Removed {} expired sessions (was: {}, now: {}, total expired: {})",
                expiredSessionIds.size(), beforeSize, activeSessions.size(), totalExpiredSessions.get());
    }

    private SessionConfig parseSessionConfig(WebSocketSession session) {
        SessionConfig config = new SessionConfig();

        String query = session.getHandshakeInfo().getUri().getQuery();
        String uri = session.getHandshakeInfo().getUri().toString();
        log.info("WebSocket connection URI: {}", uri);
        log.info("WebSocket query string: {}", query);

        if (query == null) {
            config.setSubscriptionType("all");
            log.info("No query params - subscription type: all");
            return config;
        }

        Map<String, String> params = parseQueryString(query);
        log.info("Parsed query params: {}", params);

        if ("true".equalsIgnoreCase(params.get("initial-chunked"))) {
            config.setChunkedInitial(true);
            log.info("Initial-chunked mode enabled for session");
        }

        if (params.containsKey("bounds")) {
            String[] bounds = params.get("bounds").split(",");
            if (bounds.length == 4) {
                try {
                    double lat1 = Double.parseDouble(bounds[0]);
                    double lon1 = Double.parseDouble(bounds[1]);
                    double lat2 = Double.parseDouble(bounds[2]);
                    double lon2 = Double.parseDouble(bounds[3]);

                    config.setBounds(lat1, lon1, lat2, lon2);
                    config.setSubscriptionType("bounds");
                    log.info("Configured bounds subscription: {}", config.getBoundsString());
                } catch (NumberFormatException e) {
                    log.warn("Invalid bounds format: {}", params.get("bounds"));
                }
            }
        }
        else if (params.containsKey("routes")) {
            String[] routes = params.get("routes").split(",");
            config.setRouteFilter(Set.of(routes));
            config.setSubscriptionType("routes");
            log.info("Configured routes subscription: routes={}", config.getRouteFilter());
        }
        else {
            config.setSubscriptionType("all");
            log.info("No specific filter - subscription type: all");
        }

        return config;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }

    private String getClientIp(WebSocketSession session) {
        try {
            var remoteAddress = session.getHandshakeInfo().getRemoteAddress();
            if (remoteAddress != null) {
                return remoteAddress.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP: {}", e.getMessage());
        }
        return "unknown";
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }
}
