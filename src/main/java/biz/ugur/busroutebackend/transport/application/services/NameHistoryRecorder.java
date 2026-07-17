package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.NameHistoryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.NameChangeRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class NameHistoryRecorder {

    public static final String KIND_STOP = "STOP";
    public static final String KIND_ROUTE = "ROUTE";

    private final NameHistoryRepository nameHistoryRepository;
    private final SecurityContextService securityContextService;

    public NameHistoryRecorder(NameHistoryRepository nameHistoryRepository,
                               SecurityContextService securityContextService) {
        this.nameHistoryRepository = nameHistoryRepository;
        this.securityContextService = securityContextService;
    }

    public Mono<Void> recordStopChanges(BusStop before, BusStop after) {
        List<FieldDiff> diffs = new ArrayList<>();
        addDiff(diffs, "stopName", before.getStopName(), after.getStopName());
        addDiff(diffs, "nameEn", before.getNameEn(), after.getNameEn());
        addDiff(diffs, "nameTm", before.getNameTm(), after.getNameTm());
        return record(KIND_STOP, after.getId().getValue(), diffs);
    }

    public Mono<Void> recordRouteChanges(BusRoute before, BusRoute after) {
        List<FieldDiff> diffs = new ArrayList<>();
        addDiff(diffs, "routeName", before.getRouteName(), after.getRouteName());
        addDiff(diffs, "nameEn", before.getNameEn(), after.getNameEn());
        addDiff(diffs, "nameTm", before.getNameTm(), after.getNameTm());
        return record(KIND_ROUTE, after.getId().getValue(), diffs);
    }

    private record FieldDiff(String field, String oldValue, String newValue) {
    }

    private static void addDiff(List<FieldDiff> diffs, String field, String before, String after) {
        if (!Objects.equals(normalize(before), normalize(after))) {
            diffs.add(new FieldDiff(field, before, after));
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Mono<Void> record(String kind, String entityId, List<FieldDiff> diffs) {
        if (diffs.isEmpty()) {
            return Mono.empty();
        }
        Instant now = Instant.now();
        return securityContextService.getCurrentUsername()
                .defaultIfEmpty("system")
                .flatMap(username -> nameHistoryRepository.upsertAll(diffs.stream()
                        .map(d -> new NameChangeRecord(kind, entityId, d.field(),
                                d.oldValue(), d.newValue(), username, now))
                        .toList()))
                .onErrorResume(err -> {
                    log.warn("Name history recording skipped for {} {}: {}", kind, entityId, err.getMessage());
                    return Mono.empty();
                });
    }
}
