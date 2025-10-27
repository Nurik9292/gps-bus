package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ClientRepository extends BaseRepository<Client, ClientId> {

    Mono<Client> findByPhone(String phone);

    Flux<Client> findByStatus(ClientStatus status);

    Flux<Client> findActiveClients();

    Flux<Client> findByLastActivityAfter(LocalDateTime since);

    Mono<Boolean> existsByPhone(String phone);

    Mono<Long> countByStatus(ClientStatus status);

    Mono<Long> countActiveClients();
}