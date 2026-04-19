package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.dto.CreateBusinessCommand;
import biz.ugur.busroutebackend.business.application.factory.BusinessFactory;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateBusinessUseCaseTest {

    @InjectMocks
    private CreateBusinessUseCase useCase;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessFactory businessFactory;

    @Mock
    private BusinessResponseMapper businessResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsBusinessAndReturnsResponse() {
        CreateBusinessCommand cmd = mock(CreateBusinessCommand.class);
        when(cmd.name()).thenReturn("Acme Ltd");

        Business business = Business.create(
                BusinessName.of("Acme Ltd"),
                BusinessType.RESTAURANT,
                ContactInfo.of("John", "+99312345678", "c@acme.tm", null),
                BusinessAddress.empty(),
                TaxNumber.of("12345678"),
                null,
                null,
                null
        );
        BusinessResponse response = mock(BusinessResponse.class);
        when(response.id()).thenReturn(business.getId().getValue());
        when(response.name()).thenReturn("Acme Ltd");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessFactory.create(cmd)).thenReturn(Mono.just(business));
        when(businessRepository.save(business)).thenReturn(Mono.just(business));
        when(businessResponseMapper.toResponse(business)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> assertEquals("Acme Ltd", r.name()))
                .verifyComplete();

        verify(businessRepository).save(business);
    }

    @Test
    void exposesBoundContext() {
        assertEquals("business.admin", useCase.getBoundContext());
    }
}
