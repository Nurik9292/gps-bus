package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAdminByIdUseCaseTest {

    @InjectMocks
    private GetAdminByIdUseCase useCase;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAdminWhenFound() {
        Admin admin = Admin.create("john", "hash123", "John Doe", null, false, true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(admin.getId())).thenReturn(Mono.just(admin));

        StepVerifier.create(useCase.execute(Mono.just(new GetAdminByIdUseCase.Request(admin.getId().getValue()))))
                .assertNext(result -> {
                    assertEquals("john", result.username());
                    assertEquals("John Doe", result.fullName());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenAdminNotFound() {
        String id = AdminId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(any(AdminId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(new GetAdminByIdUseCase.Request(id))))
                .expectErrorSatisfies(err -> assertInstanceOf(AdminNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
