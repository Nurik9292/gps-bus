package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class UpdateAdminStatusUseCase  implements UseCase<Mono<UpdateAdminStatusUseCase.Request>, Mono<AdminResult>> {

    private final AdminRepository adminRepository;
    private final CorrelationContextService correlationService;

    public UpdateAdminStatusUseCase(AdminRepository adminRepository, CorrelationContextService correlationService) {
        this.adminRepository = adminRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<AdminResult> execute(Mono<Request> request) {
       return  correlationService.executeWithCorrelation(
               request.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<AdminResult> executeWithCorrelation(Request request) {
       return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
           return adminRepository.findById(AdminId.of(request.id))
                           .switchIfEmpty(Mono.error(new AdminNotFoundException(request.id, "id", correlationId)))
                           .map(admin ->  {
                               if(request.status)
                                   admin.activate();
                               else admin.deactivate();

                               return admin;
                           })
                           .map(AdminResult::fromDomain);
       });
    }



    public record Request(String id, Boolean status) {}
}
