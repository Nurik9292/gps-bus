package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
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
public class GetExternalServiceByIdUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<ExternalServiceDTO> execute(String externalServiceId) {
        log.debug("Retrieving external service by ID: {}", externalServiceId);

        ExternalServiceId id = ExternalServiceId.of(externalServiceId);

        return externalServiceRepository.findById(id)
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(id)))
                .map(ExternalServiceDTOMapper::toDTO)
                .doOnSuccess(dto -> log.debug("Retrieved external service: {}", dto.name()));
    }
}
