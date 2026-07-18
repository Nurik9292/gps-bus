package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.TerminalDwellStat;
import reactor.core.publisher.Mono;

public interface TerminalDwellStatsRepository {

    Mono<TerminalDwellStat> findByKey(String routeNumber, int direction,
                                      int hourOfDay, boolean weekend);

    Mono<TerminalDwellStat> save(TerminalDwellStat stat);
}
