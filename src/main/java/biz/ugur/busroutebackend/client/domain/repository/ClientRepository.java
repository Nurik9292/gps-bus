package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;

public interface ClientRepository extends BaseRepository<Client, ClientId> {

    Mono<Client> findByPhone(String phone);

    Flux<Client> findByIds(Collection<String> ids);

    Flux<Client> findByStatus(ClientStatus status);

    Flux<Client> findActiveClients();

    Flux<Client> findByLastActivityAfter(LocalDateTime since);

    Mono<Boolean> existsByPhone(String phone);

    Mono<Long> countByStatus(ClientStatus status);

    Mono<Long> countActiveClients();

    Flux<Client> findBySpecification(Specification<Client> specification);

    Flux<Client> findBySpecification(Specification<Client> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<Client> specification);

    Mono<Client> findByServiceAndExternalUserId(String serviceId, String externalUserId);

    Flux<Client> findByCreatedByServiceId(String serviceId, Pageable pageable);

    Mono<Long> countByCreatedByServiceId(String serviceId);
}