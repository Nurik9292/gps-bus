package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.integration.application.dto.IntegrationClientDTO;
import biz.ugur.busroutebackend.integration.application.dto.IntegrationClientsPagedList;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
@Slf4j
public class GetIntegrationClientsUseCase {

    private final ExternalServiceRepository externalServiceRepository;
    private final ClientRepository clientRepository;

    public Mono<IntegrationClientsPagedList> execute(String serviceId, int page, int size) {
        log.debug("Getting clients for service: {}, page: {}, size: {}", serviceId, page, size);

        return externalServiceRepository.findById(ExternalServiceId.of(serviceId))
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(ExternalServiceId.of(serviceId))))
                .flatMap(service -> {
                    service.validateClientManagement();
                    return getClients(service.getId().getValue(), page, size);
                })
                .doOnSuccess(result -> log.debug("Retrieved {} clients for service: {}",
                        result.items().size(), serviceId))
                .doOnError(error -> log.error("Error getting clients for service: {}", serviceId, error));
    }

    private Mono<IntegrationClientsPagedList> getClients(String serviceId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());

        return Mono.zip(
                clientRepository.findByCreatedByServiceId(serviceId, pageable)
                        .map(this::toDTO)
                        .collectList(),
                clientRepository.countByCreatedByServiceId(serviceId)
        ).map(tuple -> IntegrationClientsPagedList.of(tuple.getT1(), tuple.getT2(), page, size));
    }

    private IntegrationClientDTO toDTO(Client client) {
        return IntegrationClientDTO.builder()
                .clientId(client.getId().getValue())
                .name(client.getName())
                .externalUserId(client.getExternalUserId())
                .status(client.getStatus().name())
                .lastActivity(client.getLastActivity())
                .createdAt(client.getCreatedAt())
                .build();
    }
}
