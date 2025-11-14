package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
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
public class BlockExternalServiceUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<ExternalServiceDTO> execute(String externalServiceId, AdminId blockedByAdminId, String reason) {
        log.info("Blocking external service: {} by admin: {}", externalServiceId, blockedByAdminId.getValue());

        ExternalServiceId id = ExternalServiceId.of(externalServiceId);

        return externalServiceRepository.findById(id)
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(id)))
                .map(service -> service.block(blockedByAdminId, reason))
                .flatMap(externalServiceRepository::save)
                .map(ExternalServiceDTOMapper::toDTO)
                .doOnSuccess(dto -> log.info("Blocked external service: {}", dto.name()))
                .doOnError(error -> log.error("Error blocking external service: {}", externalServiceId, error));
    }
}
