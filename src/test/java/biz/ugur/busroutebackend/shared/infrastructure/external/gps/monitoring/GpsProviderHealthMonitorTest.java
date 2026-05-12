package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GpsProviderHealthMonitorTest {

    @Mock
    private EmailNotificationService emailService;

    private GpsAlertProperties properties;
    private GpsProviderHealthMonitor monitor;
    private Clock clock;

    private static final Instant T0 = Instant.parse("2026-05-12T10:00:00Z");

    @BeforeEach
    void setUp() {
        properties = new GpsAlertProperties();
        properties.setEnabled(true);
        properties.setRecipients("ops@example.com");
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        monitor = new GpsProviderHealthMonitor(emailService, properties, clock);
        when(emailService.sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());
    }

    @Test
    void threeConsecutiveErrorsTriggerHttpErrorAlert() {
        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));
        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));
        verify(emailService, never()).sendGpsAlert(any(), any(), any(), any(), any());

        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));

        ArgumentCaptor<AlertKind> kindCap = ArgumentCaptor.forClass(AlertKind.class);
        verify(emailService, times(1)).sendGpsAlert(
                eq(List.of("ops@example.com")),
                eq("TUGDK:BALKAN"),
                kindCap.capture(),
                anyString(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals(AlertKind.HTTP_ERROR, kindCap.getValue());
    }

    @Test
    void successResetsConsecutiveFailures() {
        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));
        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));
        monitor.recordFetch("TUGDK:BALKAN", new FetchOutcome.Success(50, 50, T0));

        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));
        monitor.recordError("TUGDK:BALKAN", new RuntimeException("boom"));
        verify(emailService, never()).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void threeConsecutiveEmptyTriggerEmptyAlert() {
        monitor.recordFetch("TUGDK:ASHGABAT", new FetchOutcome.Empty());
        monitor.recordFetch("TUGDK:ASHGABAT", new FetchOutcome.Empty());
        verify(emailService, never()).sendGpsAlert(any(), any(), any(), any(), any());

        monitor.recordFetch("TUGDK:ASHGABAT", new FetchOutcome.Empty());

        ArgumentCaptor<AlertKind> kindCap = ArgumentCaptor.forClass(AlertKind.class);
        verify(emailService).sendGpsAlert(
                eq(List.of("ops@example.com")),
                eq("TUGDK:ASHGABAT"),
                kindCap.capture(),
                anyString(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals(AlertKind.EMPTY, kindCap.getValue());
    }
}
