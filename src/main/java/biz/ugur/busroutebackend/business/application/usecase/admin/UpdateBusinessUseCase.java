package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.dto.UpdateBusinessCommand;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessNotFoundException;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateBusinessUseCase
        extends BaseUseCase<UpdateBusinessUseCase.Request, BusinessResponse> {

    public record Request(String businessId, UpdateBusinessCommand command) {}

    private final BusinessRepository businessRepository;
    private final BusinessResponseMapper businessResponseMapper;

    public UpdateBusinessUseCase(BusinessRepository businessRepository,
                                  BusinessResponseMapper businessResponseMapper,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.businessRepository = businessRepository;
        this.businessResponseMapper = businessResponseMapper;
    }

    @Override
    protected Mono<BusinessResponse> process(Request request) {
        return processInternal(request.businessId(), request.command());
    }

    @Override
    protected String getBoundContext() {
        return "business.admin";
    }

    private Mono<BusinessResponse> processInternal(String businessId, UpdateBusinessCommand cmd) {
        return businessRepository.findById(BusinessId.of(businessId))
                .switchIfEmpty(Mono.error(new BusinessNotFoundException(businessId)))
                .map(existing -> existing.updateProfile(
                        cmd.name() != null ? BusinessName.of(cmd.name()) : null,
                        cmd.businessType() != null ? BusinessType.from(cmd.businessType()) : null,
                        cmd.contactPhone() != null ? ContactInfo.of(
                                cmd.contactPerson(), cmd.contactPhone(),
                                cmd.contactEmail(), cmd.websiteUrl()) : null,
                        (cmd.country() != null || cmd.city() != null || cmd.addressLine() != null)
                                ? BusinessAddress.of(cmd.country(), cmd.city(), cmd.addressLine())
                                : null,
                        cmd.taxNumber() != null && !cmd.taxNumber().isBlank()
                                ? TaxNumber.of(cmd.taxNumber()) : null,
                        cmd.registrationNumber(),
                        cmd.logoUrl(),
                        cmd.description()))
                .flatMap(businessRepository::save)
                .flatMap(businessResponseMapper::toResponse)
                .doOnSuccess(r -> log.info("Business updated: id={}", r.id()));
    }
}
