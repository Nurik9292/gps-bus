package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceListDTO;
import biz.ugur.busroutebackend.integration.application.mapper.ExternalServiceDTOMapper;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetAllExternalServicesUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<ExternalServiceListDTO> execute() {
        log.debug("Retrieving all external services");

        return Mono.zip(
                externalServiceRepository.findAll()
                        .map(ExternalServiceDTOMapper::toDTO)
                        .collectList(),
                externalServiceRepository.count(),
                externalServiceRepository.countActive()
        ).map(tuple -> {
            List<ExternalServiceDTO> services = tuple.getT1();
            Long totalCount = tuple.getT2();
            Long activeCount = tuple.getT3();

            return ExternalServiceListDTO.builder()
                    .services(services)
                    .totalCount(totalCount)
                    .activeCount(activeCount)
                    .build();
        }).doOnSuccess(list -> log.debug("Retrieved {} external services", list.totalCount()));
    }

    public Mono<ExternalServiceListDTO> executeActiveOnly() {
        log.debug("Retrieving active external services");

        return Mono.zip(
                externalServiceRepository.findAllActive()
                        .map(ExternalServiceDTOMapper::toDTO)
                        .collectList(),
                externalServiceRepository.countActive()
        ).map(tuple -> {
            List<ExternalServiceDTO> services = tuple.getT1();
            long activeCount = tuple.getT2();

            return ExternalServiceListDTO.builder()
                    .services(services)
                    .totalCount(activeCount)
                    .activeCount(activeCount)
                    .build();
        }).doOnSuccess(list -> log.debug("Retrieved {} active external services", list.activeCount()));
    }
}
