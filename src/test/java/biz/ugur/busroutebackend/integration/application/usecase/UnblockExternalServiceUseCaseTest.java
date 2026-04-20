package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UnblockExternalServiceUseCaseTest {

    @InjectMocks
    private UnblockExternalServiceUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    private ExternalService blockedService;
    private AdminId adminId;

    @BeforeEach
    void setUp() {
        adminId = AdminId.generate();
        ExternalService created = ExternalService.create(
                "Partner",
                "description",
                AdminId.generate(),
                List.of("/api/v1/**"),
                120,
                false
        );
        blockedService = created.block(AdminId.generate(), "prior block");
    }

    @Test
    void unblocksExistingServiceAndReturnsDTO() {
        when(externalServiceRepository.findById(blockedService.getId())).thenReturn(Mono.just(blockedService));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(blockedService.getId().getValue(), adminId))
                .assertNext(dto -> {
                    assertEquals("Partner", dto.name());
                    assertTrue(dto.isActive());
                })
                .verifyComplete();

        verify(externalServiceRepository).save(any(ExternalService.class));
    }

    @Test
    void errorsWhenServiceNotFound() {
        String id = ExternalServiceId.generate().getValue();
        when(externalServiceRepository.findById(any(ExternalServiceId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id, adminId))
                .expectErrorSatisfies(err -> assertInstanceOf(ExternalServiceNotFoundException.class, err))
                .verify();

        verify(externalServiceRepository, never()).save(any());
    }
}
