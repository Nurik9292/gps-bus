package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
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
class ChangePasswordUseCaseTest {

    @InjectMocks
    private ChangePasswordUseCase useCase;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void changesPasswordWhenCurrentIsCorrect() {
        Admin admin = Admin.create("john", "$encoded$", "John", null, false, true);
        ChangePasswordUseCase.Request req = new ChangePasswordUseCase.Request(
                admin.getId(), "old-password", "new-password", "access-token");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(admin.getId())).thenReturn(Mono.just(admin));
        when(passwordEncoder.matches("old-password", admin.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("$new-encoded$");
        when(adminRepository.save(any(Admin.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(tokenBlacklistService.blacklistAccessToken("access-token")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(req)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void errorsWhenCurrentPasswordIncorrect() {
        Admin admin = Admin.create("john", "$encoded$", "John", null, false, true);
        ChangePasswordUseCase.Request req = new ChangePasswordUseCase.Request(
                admin.getId(), "wrong", "new", "token");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(admin.getId())).thenReturn(Mono.just(admin));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        StepVerifier.create(useCase.execute(Mono.just(req)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void errorsWhenAdminNotFound() {
        ChangePasswordUseCase.Request req = new ChangePasswordUseCase.Request(
                AdminId.generate(), "old", "new", "token");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findById(any(AdminId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(req)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
