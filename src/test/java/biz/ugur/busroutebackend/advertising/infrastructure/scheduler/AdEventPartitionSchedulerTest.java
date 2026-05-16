package biz.ugur.busroutebackend.advertising.infrastructure.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Mono;

import java.util.Map;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdEventPartitionSchedulerTest {

    @Mock private DatabaseClient db;
    @Mock private DatabaseClient.GenericExecuteSpec spec;

    private AdEventPartitionAlertProperties properties;
    private AdEventPartitionScheduler scheduler;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T03:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        properties = new AdEventPartitionAlertProperties();
        properties.setLookaheadMonths(2);
        scheduler = new AdEventPartitionScheduler(db, clock, properties);
    }

    @SuppressWarnings("unchecked")
    @Test
    void ensurePartitions_executesCreateForEachTableAndMonth() {
        FetchSpec<Map<String, Object>> fetch = mock(FetchSpec.class);
        when(db.sql(anyString())).thenReturn(spec);
        when(spec.fetch()).thenReturn(fetch);
        when(fetch.rowsUpdated()).thenReturn(Mono.just(0L));

        scheduler.ensurePartitions();

        int statementsPerPartition = 3;
        int tables = 3;
        int months = properties.getLookaheadMonths() + 1;
        verify(db, times(months * tables * statementsPerPartition)).sql(anyString());
    }
}
