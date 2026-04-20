package biz.ugur.busroutebackend.business.application.usecase.admin;

import biz.ugur.busroutebackend.business.application.dto.BusinessPaginationQuery;
import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.application.mapper.BusinessResponseMapper;
import biz.ugur.busroutebackend.business.domain.enums.BusinessStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetBusinessesPaginatedUseCaseTest {

    @InjectMocks
    private GetBusinessesPaginatedUseCase useCase;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessResponseMapper businessResponseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private Business buildBusiness() {
        return Business.create(
                BusinessName.of("Acme"),
                BusinessType.RESTAURANT,
                ContactInfo.of("John", "+99312345678", "c@acme.tm", null),
                BusinessAddress.empty(),
                TaxNumber.of("12345678"),
                null, null, null
        );
    }

    @Test
    void returnsAllBusinessesWhenNoFilter() {
        Business business = buildBusiness();
        BusinessResponse response = mock(BusinessResponse.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(business));
        when(businessRepository.count()).thenReturn(Mono.just(1L));
        when(businessRepository.countByStatus(BusinessStatus.APPROVED)).thenReturn(Mono.just(0L));
        when(businessResponseMapper.toResponse(business)).thenReturn(Mono.just(response));

        BusinessPaginationQuery q = BusinessPaginationQuery.create(1, 10, "name", "asc", null, null);

        StepVerifier.create(useCase.execute(q))
                .assertNext(list -> assertEquals(1, list.getBusinesses().size()))
                .verifyComplete();
    }

    @Test
    void filtersByStatus() {
        Business business = buildBusiness();
        BusinessResponse response = mock(BusinessResponse.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.findByStatus(any(BusinessStatus.class), any(Pageable.class)))
                .thenReturn(Flux.just(business));
        when(businessRepository.countByStatus(BusinessStatus.PENDING)).thenReturn(Mono.just(1L));
        when(businessRepository.countByStatus(BusinessStatus.APPROVED)).thenReturn(Mono.just(5L));
        when(businessResponseMapper.toResponse(business)).thenReturn(Mono.just(response));

        BusinessPaginationQuery q = BusinessPaginationQuery.create(
                1, 10, null, null, "PENDING", null);

        StepVerifier.create(useCase.execute(q))
                .assertNext(list -> assertEquals(1, list.getBusinesses().size()))
                .verifyComplete();
    }

    @Test
    void filtersByType() {
        Business business = buildBusiness();
        BusinessResponse response = mock(BusinessResponse.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(businessRepository.findByType(any(BusinessType.class), any(Pageable.class)))
                .thenReturn(Flux.just(business));
        when(businessRepository.count()).thenReturn(Mono.just(1L));
        when(businessRepository.countByStatus(BusinessStatus.APPROVED)).thenReturn(Mono.just(3L));
        when(businessResponseMapper.toResponse(business)).thenReturn(Mono.just(response));

        BusinessPaginationQuery q = BusinessPaginationQuery.create(
                1, 10, null, null, null, "RESTAURANT");

        StepVerifier.create(useCase.execute(q))
                .assertNext(list -> assertEquals(1, list.getBusinesses().size()))
                .verifyComplete();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("business.admin", useCase.getBoundContext());
    }
}
