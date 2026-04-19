package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminPaginationQuery;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllAdminsUseCaseTest {

    @InjectMocks
    private GetAllAdminsUseCase useCase;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAllAdminsWhenNoFilter() {
        Admin a = Admin.create("user1", "hash", "User One", null, false, true);
        Admin b = Admin.create("user2", "hash", "User Two", null, true, true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findAll()).thenReturn(Flux.just(a, b));
        when(adminRepository.count()).thenReturn(Mono.just(2L));
        when(adminRepository.countActiveAdmins()).thenReturn(Mono.just(2L));

        AdminPaginationQuery q = AdminPaginationQuery.fromParams(1, 10, null, null, null, null, null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(list -> {
                    assertEquals(2, list.getAdmins().size());
                    assertEquals(2L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void appliesSearchSpecification() {
        Admin a = Admin.create("john", "hash", "John", null, false, true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(a));
        when(adminRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(adminRepository.countActiveAdmins()).thenReturn(Mono.just(5L));

        AdminPaginationQuery q = AdminPaginationQuery.fromParams(1, 10, null, null, true, "john", null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(list -> {
                    assertEquals(1, list.getAdmins().size());
                    assertEquals(5L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
