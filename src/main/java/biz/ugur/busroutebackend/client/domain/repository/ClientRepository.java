package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ClientRepository {

    Mono<Client> save(Client client);

    Mono<Client> findById(ClientId clientId);

    Mono<Client> findByPhone(String phone);

    Flux<Client> findByStatus(ClientStatus status);

    Flux<Client> findActiveClients();

    Flux<Client> findByLastActivityAfter(Instant since);

    Mono<Boolean> existsByPhone(String phone);

    Mono<Void> deleteById(ClientId clientId);

    Mono<Long> countByStatus(ClientStatus status);

    Mono<Long> countActiveClients();
}