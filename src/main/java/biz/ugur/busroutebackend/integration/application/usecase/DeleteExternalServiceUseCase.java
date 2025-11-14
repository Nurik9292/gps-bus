package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.domain.events.ExternalServiceDeletedEvent;
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
public class DeleteExternalServiceUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<Void> execute(String externalServiceId, AdminId deletedByAdminId) {
        log.info("Deleting external service: {} by admin: {}", externalServiceId, deletedByAdminId.getValue());

        ExternalServiceId id = ExternalServiceId.of(externalServiceId);

        return externalServiceRepository.findById(id)
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(id)))
                .flatMap(service -> {
                    ExternalServiceDeletedEvent event = new ExternalServiceDeletedEvent(
                            service.getId().getValue(),
                            service.getName(),
                            deletedByAdminId.getValue()
                    );

                    return externalServiceRepository.deleteById(id)
                            .doOnSuccess(v -> log.info("Deleted external service: {}", service.getName()));
                })
                .doOnError(error -> log.error("Error deleting external service: {}", externalServiceId, error));
    }
}
