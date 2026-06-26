package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.Phone;
import biz.ugur.busroutebackend.integration.application.dto.IntegrationClientDTO;
import biz.ugur.busroutebackend.integration.application.dto.RegisterIntegrationClientRequest;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.exceptions.IntegrationClientAlreadyExistsException;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterIntegrationClientUseCase {

    private final ExternalServiceRepository externalServiceRepository;
    private final ClientRepository clientRepository;

    public Mono<IntegrationClientDTO> execute(String serviceId, RegisterIntegrationClientRequest request) {
        log.info("Registering integration client for service: {}, externalUserId: {}",
                serviceId, request.externalUserId());

        return externalServiceRepository.findById(ExternalServiceId.of(serviceId))
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(ExternalServiceId.of(serviceId))))
                .flatMap(service -> {
                    service.validateClientManagement();
                    return registerClient(service.getId().getValue(), request);
                })
                .doOnSuccess(dto -> log.info("Successfully registered integration client: {} for service: {}",
                        dto.clientId(), serviceId))
                .doOnError(error -> log.error("Error registering integration client for service: {}",
                        serviceId, error));
    }

    private Mono<IntegrationClientDTO> registerClient(String serviceId,
                                                       RegisterIntegrationClientRequest request) {
        return clientRepository.findByServiceAndExternalUserId(serviceId, request.externalUserId())
                .flatMap(existing -> Mono.<IntegrationClientDTO>error(
                        new IntegrationClientAlreadyExistsException(serviceId, request.externalUserId())
                ))
                .switchIfEmpty(Mono.defer(() -> createOrMerge(serviceId, request)));
    }

    private Mono<IntegrationClientDTO> createOrMerge(String serviceId,
                                                      RegisterIntegrationClientRequest request) {
        String canonicalPhone = Phone.canonicalWithoutPlusOrNull(request.phone());
        if (canonicalPhone == null) {
            return createNewClient(serviceId, request, null);
        }
        return clientRepository.findByPhone(canonicalPhone)
                .flatMap(existing -> existing.getExternalUserId() == null
                        ? clientRepository.save(existing.linkExternalService(serviceId, request.externalUserId()))
                                .map(this::toDTO)
                        : createNewClient(serviceId, request, null))
                .switchIfEmpty(Mono.defer(() -> createNewClient(serviceId, request, canonicalPhone)));
    }

    private Mono<IntegrationClientDTO> createNewClient(String serviceId,
                                                        RegisterIntegrationClientRequest request,
                                                        String canonicalPhone) {
        Client client = Client.createViaExternalService(
                request.name(),
                serviceId,
                request.externalUserId(),
                canonicalPhone
        );

        return clientRepository.save(client)
                .map(this::toDTO);
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
