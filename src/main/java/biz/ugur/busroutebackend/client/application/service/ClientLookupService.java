package biz.ugur.busroutebackend.client.application.service;

import biz.ugur.busroutebackend.client.application.dto.ClientSummary;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;

@Service
public class ClientLookupService {

    private final ClientRepository clientRepository;

    public ClientLookupService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Mono<ClientSummary> findById(String clientId) {
        return clientRepository.findById(ClientId.of(clientId))
                .map(ClientSummary::fromDomain);
    }

    public Mono<Map<String, ClientSummary>> findByIds(Collection<String> clientIds) {
        if (clientIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        return clientRepository.findByIds(clientIds)
                .map(ClientSummary::fromDomain)
                .collectMap(ClientSummary::clientId);
    }
}
