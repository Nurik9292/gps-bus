package biz.ugur.busroutebackend.interfaces.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsSessionRegistryTest {

    @Mock
    private WebSocketSession session;

    @Mock
    private HandshakeInfo handshakeInfo;

    private WsSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WsSessionRegistry();
        lenient().when(session.getHandshakeInfo()).thenReturn(handshakeInfo);
        lenient().when(handshakeInfo.getRemoteAddress())
                .thenReturn(new InetSocketAddress("192.168.0.1", 12345));
    }

    @Test
    void registerWithRoutesQueryParsesRouteFilter() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=160,1,2"));

        String sessionId = registry.register(session);

        assertThat(sessionId).startsWith("ws-1-");
        SessionConfig cfg = registry.get(sessionId).orElseThrow();
        assertThat(cfg.getSubscriptionType()).isEqualTo("routes");
        assertThat(cfg.getRouteFilter()).containsExactlyInAnyOrder("160", "1", "2");
        assertThat(cfg.getClientIp()).isEqualTo("192.168.0.1");
        assertThat(cfg.getCityFilter()).isNull();
    }

    @Test
    void registerWithRoutesAndCityQueryParsesCityFilter() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=160&cityId=city-006"));

        String sessionId = registry.register(session);

        SessionConfig cfg = registry.get(sessionId).orElseThrow();
        assertThat(cfg.getSubscriptionType()).isEqualTo("routes");
        assertThat(cfg.getRouteFilter()).containsExactly("160");
        assertThat(cfg.getCityFilter()).isEqualTo("city-006");
    }

    @Test
    void registerWithBlankCityQueryLeavesCityFilterEmpty() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=160&cityId="));

        String sessionId = registry.register(session);

        assertThat(registry.get(sessionId).orElseThrow().getCityFilter()).isNull();
    }

    @Test
    void registerWithBoundsQueryParsesBounds() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?bounds=37.9,58.3,38.0,58.4"));

        String sessionId = registry.register(session);

        SessionConfig cfg = registry.get(sessionId).orElseThrow();
        assertThat(cfg.getSubscriptionType()).isEqualTo("bounds");
        assertThat(cfg.isInBounds(37.95, 58.35)).isTrue();
        assertThat(cfg.isInBounds(40.0, 60.0)).isFalse();
    }

    @Test
    void registerWithoutQueryDefaultsToAll() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions"));

        String sessionId = registry.register(session);

        SessionConfig cfg = registry.get(sessionId).orElseThrow();
        assertThat(cfg.getSubscriptionType()).isEqualTo("all");
    }

    @Test
    void sessionIdsAreUniquePerRegistration() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions"));

        String id1 = registry.register(session);
        String id2 = registry.register(session);
        String id3 = registry.register(session);

        assertThat(id1).isNotEqualTo(id2).isNotEqualTo(id3);
        assertThat(registry.activeCount()).isEqualTo(3);
    }

    @Test
    void removeReturnsConfigAndDecrementsCount() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=160"));

        String sessionId = registry.register(session);
        assertThat(registry.activeCount()).isEqualTo(1);

        SessionConfig removed = registry.remove(sessionId);

        assertThat(removed).isNotNull();
        assertThat(removed.getRouteFilter()).containsExactly("160");
        assertThat(registry.activeCount()).isZero();
        assertThat(registry.get(sessionId)).isEmpty();
    }

    @Test
    void getMissingSessionReturnsEmpty() {
        assertThat(registry.get("nonexistent")).isEmpty();
    }

    @Test
    void subscriptionTypeCountsAggregatesAcrossSessions() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=1"));
        registry.register(session);
        registry.register(session);

        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions"));
        registry.register(session);

        var counts = registry.subscriptionTypeCounts();
        assertThat(counts).containsEntry("routes", 2L).containsEntry("all", 1L);
    }

    @Test
    void cleanupExpiredRemovesIdleSessions() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=160"));
        String sessionId = registry.register(session);

        SessionConfig cfg = registry.get(sessionId).orElseThrow();
        java.lang.reflect.Field lastActivityField;
        try {
            lastActivityField = SessionConfig.class.getDeclaredField("lastActivityTime");
            lastActivityField.setAccessible(true);
            lastActivityField.set(cfg, java.time.Instant.now().minus(Duration.ofMinutes(10)));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not back-date lastActivityTime", e);
        }

        registry.cleanupExpired();

        assertThat(registry.activeCount()).isZero();
        assertThat(registry.totalExpired()).isEqualTo(1);
        assertThat(registry.get(sessionId)).isEmpty();
    }

    @Test
    void cleanupExpiredKeepsActiveSessions() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions?routes=160"));
        registry.register(session);

        registry.cleanupExpired();

        assertThat(registry.activeCount()).isEqualTo(1);
        assertThat(registry.totalExpired()).isZero();
    }

    @Test
    void snapshotForLogReturnsUnmodifiableCopy() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions"));
        registry.register(session);

        var snapshot = registry.snapshotForLog();

        assertThat(snapshot).hasSize(1);
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.put("foo", new SessionConfig()));
    }

    @Test
    void clientIpFallsBackToUnknownWhenAddressMissing() {
        when(handshakeInfo.getUri())
                .thenReturn(URI.create("http://host/api/v1/ws/vehicle-positions"));
        when(handshakeInfo.getRemoteAddress()).thenReturn(null);

        String sessionId = registry.register(session);

        assertThat(registry.get(sessionId).orElseThrow().getClientIp()).isEqualTo("unknown");
    }
}
