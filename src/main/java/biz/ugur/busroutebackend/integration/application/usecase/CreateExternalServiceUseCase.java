package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.application.dto.CreateExternalServiceRequest;
import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
import biz.ugur.busroutebackend.integration.application.mapper.ExternalServiceDTOMapper;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
@Slf4j
public class CreateExternalServiceUseCase {

    private final ExternalServiceRepository externalServiceRepository;

    public Mono<ExternalServiceDTO> execute(CreateExternalServiceRequest request, AdminId createdByAdminId) {
        log.info("Creating external service: {} by admin: {}", request.name(), createdByAdminId.getValue());

        return externalServiceRepository.existsByName(request.name())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "External service with name '" + request.name() + "' already exists"
                        ));
                    }

                    ExternalService service = ExternalService.create(
                            request.name(),
                            request.description(),
                            createdByAdminId,
                            request.allowedEndpoints(),
                            request.rateLimitPerMinute()
                    );

                    return externalServiceRepository.save(service);
                })
                .map(ExternalServiceDTOMapper::toDTOWithToken)
                .doOnSuccess(dto -> log.info("Created external service: {} with ID: {}",
                        dto.name(), dto.id()))
                .doOnError(error -> log.error("Error creating external service: {}", request.name(), error));
    }
}
