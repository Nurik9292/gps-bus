package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class LogoutUseCaseTest {

    @InjectMocks
    private LogoutUseCase useCase;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void blacklistsAccessTokenOnLogout() {
        AdminId adminId = AdminId.generate();
        String accessToken = "eyJhbGciOiJIUzI1NiJ9.token";

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tokenBlacklistService.blacklistAccessToken(accessToken)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new LogoutUseCase.Request(adminId, accessToken))))
                .verifyComplete();

        verify(tokenBlacklistService).blacklistAccessToken(accessToken);
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
