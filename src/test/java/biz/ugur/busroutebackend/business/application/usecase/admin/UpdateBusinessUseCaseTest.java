package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.dto.UpdateBusinessCommand;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessNotFoundException;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateBusinessUseCaseTest {

    @InjectMocks
    private UpdateBusinessUseCase useCase;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessResponseMapper businessResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private Business existing;

    @BeforeEach
    void setUp() {
        existing = Business.create(
                BusinessName.of("Acme Ltd"),
                BusinessType.RESTAURANT,
                ContactInfo.of("John", "+99312345678", "c@acme.tm", null),
                BusinessAddress.empty(),
                TaxNumber.of("12345678"),
                null,
                null,
                null
        );
    }

    @Test
    void updatesFieldsAndReturnsResponse() {
        UpdateBusinessCommand cmd = new UpdateBusinessCommand(
                "New Name", null, null, null,
                "Jane", "+99312000000", "j@new.tm", "https://new.tm",
                "TM", "Ashgabat", "Main street 1",
                null, "new description"
        );
        BusinessResponse response = mock(BusinessResponse.class);
        when(response.id()).thenReturn(existing.getId().getValue());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(businessRepository.save(any(Business.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(businessResponseMapper.toResponse(any(Business.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(new UpdateBusinessUseCase.Request(
                existing.getId().getValue(), cmd)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void errorsWhenBusinessNotFound() {
        UpdateBusinessCommand cmd = new UpdateBusinessCommand(
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.findById(any(BusinessId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new UpdateBusinessUseCase.Request(
                BusinessId.generate().getValue(), cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(BusinessNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("business.admin", useCase.getBoundContext());
    }
}
