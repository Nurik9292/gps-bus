package biz.ugur.busroutebackend.transport.application.usecase.routeswap;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapSummaryDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapVerdictDTO;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetRouteSwapVerdictsUseCase {

    private static final int MAX_LIMIT = 500;

    private final RouteSwapAuditRepository auditRepository;
    private final CorrelationContextService correlationService;

    public Mono<List<RouteSwapVerdictDTO>> list(LocalDate fromDate, LocalDate toDate,
                                                String verdict, Integer limit) {
        LocalDate today = ShiftType.operationalDateAt(Instant.now());
        LocalDate from = fromDate != null ? fromDate : today;
        LocalDate to = toDate != null ? toDate : today;
        int effectiveLimit = limit == null ? 200 : Math.clamp(limit, 1, MAX_LIMIT);
        return correlationService.executeWithCorrelation(
                auditRepository.findVerdicts(from, to, verdict, effectiveLimit)
                        .map(RouteSwapVerdictDTO::fromRecord)
                        .collectList(),
                "transport");
    }

    public Mono<RouteSwapSummaryDTO> summary() {
        Instant now = Instant.now();
        LocalDate operationalDate = ShiftType.operationalDateAt(now);
        String shift = ShiftType.operationalShiftAt(now).map(Enum::name).orElse(null);
        return correlationService.executeWithCorrelation(
                auditRepository.countVerdictsByType(operationalDate, shift)
                        .collectList()
                        .map(counts -> RouteSwapSummaryDTO.of(operationalDate, shift, counts)),
                "transport");
    }
}
