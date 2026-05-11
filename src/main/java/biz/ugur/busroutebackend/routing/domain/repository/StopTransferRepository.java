package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.routing.domain.model.raptor.StopTransfer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface StopTransferRepository {

    Flux<StopTransfer> saveAll(List<StopTransfer> transfers);

    Mono<Long> count();

    Mono<Long> deleteAll();
}
