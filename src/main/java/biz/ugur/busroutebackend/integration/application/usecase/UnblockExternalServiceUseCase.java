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
public class UnblockExternalServiceUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<ExternalServiceDTO> execute(String externalServiceId, AdminId unblockedByAdminId) {
        log.info("Unblocking external service: {} by admin: {}", externalServiceId, unblockedByAdminId.getValue());

        ExternalServiceId id = ExternalServiceId.of(externalServiceId);

        return externalServiceRepository.findById(id)
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(id)))
                .map(service -> service.unblock(unblockedByAdminId))
                .flatMap(externalServiceRepository::save)
                .map(ExternalServiceDTOMapper::toDTO)
                .doOnSuccess(dto -> log.info("Unblocked external service: {}", dto.name()))
                .doOnError(error -> log.error("Error unblocking external service: {}", externalServiceId, error));
    }
}
