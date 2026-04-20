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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateAdminStatusUseCaseTest {

    @InjectMocks
    private UpdateAdminStatusUseCase useCase;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void activatesAdminWhenStatusTrue() {
        Admin admin = Admin.create("user", "hash", "User", null, false, false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(admin.getId())).thenReturn(Mono.just(admin));
        when(adminRepository.save(any(Admin.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(
                new UpdateAdminStatusUseCase.Request(admin.getId().getValue(), true))))
                .assertNext(result -> assertTrue(result.isActive()))
                .verifyComplete();
    }

    @Test
    void deactivatesAdminWhenStatusFalse() {
        Admin admin = Admin.create("user", "hash", "User", null, false, true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(admin.getId())).thenReturn(Mono.just(admin));
        when(adminRepository.save(any(Admin.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(
                new UpdateAdminStatusUseCase.Request(admin.getId().getValue(), false))))
                .assertNext(result -> assertFalse(result.isActive()))
                .verifyComplete();
    }

    @Test
    void errorsWhenAdminNotFound() {
        String id = AdminId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(any(AdminId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new UpdateAdminStatusUseCase.Request(id, true))))
                .expectErrorSatisfies(err -> assertInstanceOf(AdminNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
