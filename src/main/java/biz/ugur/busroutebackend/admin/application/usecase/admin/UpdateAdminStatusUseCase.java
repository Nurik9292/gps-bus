package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class UpdateAdminStatusUseCase  extends BaseUseCase<Mono<UpdateAdminStatusUseCase.Request>, AdminResult> {

    private final AdminRepository adminRepository;
    private final CorrelationContextService correlationService;

    public UpdateAdminStatusUseCase(AdminRepository adminRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.correlationService = correlationService;
    }



    @Override
    protected Mono<AdminResult> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<AdminResult> processInternal(Request request) {
       return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
           return adminRepository.findById(AdminId.of(request.id))
                           .switchIfEmpty(Mono.error(new AdminNotFoundException(request.id, "id", correlationId)))
                           .map(admin ->  {
                               // Admin is immutable - activate()/deactivate() return new instances
                               if(request.status)
                                   return admin.activate();
                               else
                                   return admin.deactivate();
                           })
                           .flatMap(adminRepository::save)
                           .map(AdminResult::fromDomain);
       });
    }



    public record Request(String id, Boolean status) {}
}
