package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
import biz.ugur.busroutebackend.integration.application.dto.UpdateExternalServiceRequest;
import biz.ugur.busroutebackend.integration.application.mapper.ExternalServiceDTOMapper;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateExternalServiceUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<ExternalServiceDTO> execute(String externalServiceId, UpdateExternalServiceRequest request) {
        log.info("Updating external service: {}", externalServiceId);

        ExternalServiceId id = ExternalServiceId.of(externalServiceId);

        return externalServiceRepository.findById(id)
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(id)))
                .flatMap(service -> {
                    if (!service.getName().equals(request.name())) {
                        return externalServiceRepository.existsByName(request.name())
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new IllegalArgumentException(
                                                "External service with name '" + request.name() + "' already exists"
                                        ));
                                    }
                                    return Mono.just(service);
                                });
                    }
                    return Mono.just(service);
                })
                .map(service -> service.update(
                        request.name(),
                        request.description(),
                        request.allowedEndpoints(),
                        request.rateLimitPerMinute()
                ))
                .flatMap(externalServiceRepository::save)
                .map(ExternalServiceDTOMapper::toDTO)
                .doOnSuccess(dto -> log.info("Updated external service: {}", dto.name()))
                .doOnError(error -> log.error("Error updating external service: {}", externalServiceId, error));
    }
}
