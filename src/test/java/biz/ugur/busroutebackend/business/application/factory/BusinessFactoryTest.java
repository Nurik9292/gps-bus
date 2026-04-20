package biz.ugur.busroutebackend.business.application.factory;

import biz.ugur.busroutebackend.business.application.dto.CreateBusinessCommand;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class BusinessFactoryTest {

    @InjectMocks
    private BusinessFactory factory;

    @Mock
    private BusinessRepository businessRepository;

    @Test
    void createsBusinessWithUniquePhoneAndTax() {
        CreateBusinessCommand cmd = new CreateBusinessCommand(
                "Acme Ltd", "RESTAURANT", "12345678", "REG-1",
                "John", "+99312345678", "c@acme.tm", null,
                "TM", "Ashgabat", "Main street 1",
                null, "description"
        );

        when(businessRepository.existsByContactPhone("+99312345678")).thenReturn(Mono.just(false));
        when(businessRepository.existsByTaxNumber("12345678")).thenReturn(Mono.just(false));

        StepVerifier.create(factory.create(cmd))
                .assertNext(business -> {
                    assertEquals("Acme Ltd", business.getName().getValue());
                    assertEquals(BusinessType(), business.getType().name());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenPhoneAlreadyExists() {
        CreateBusinessCommand cmd = new CreateBusinessCommand(
                "Acme Ltd", "RESTAURANT", "12345678", null,
                "John", "+99312345678", "c@acme.tm", null,
                null, null, null, null, null
        );

        when(businessRepository.existsByContactPhone(anyString())).thenReturn(Mono.just(true));
        when(businessRepository.existsByTaxNumber(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(factory.create(cmd))
                .expectErrorSatisfies(err -> assertInstanceOf(BusinessValidationException.class, err))
                .verify();
    }

    @Test
    void errorsWhenTaxAlreadyExists() {
        CreateBusinessCommand cmd = new CreateBusinessCommand(
                "Acme Ltd", "RESTAURANT", "12345678", null,
                "John", "+99312345678", "c@acme.tm", null,
                null, null, null, null, null
        );

        when(businessRepository.existsByContactPhone(anyString())).thenReturn(Mono.just(false));
        when(businessRepository.existsByTaxNumber("12345678")).thenReturn(Mono.just(true));

        StepVerifier.create(factory.create(cmd))
                .expectErrorSatisfies(err -> assertInstanceOf(BusinessValidationException.class, err))
                .verify();
    }

    @Test
    void skipsTaxCheckWhenTaxIsNull() {
        CreateBusinessCommand cmd = new CreateBusinessCommand(
                "Acme Ltd", "RESTAURANT", null, null,
                "John", "+99312345678", "c@acme.tm", null,
                null, null, null, null, null
        );

        when(businessRepository.existsByContactPhone(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(factory.create(cmd))
                .assertNext(business -> assertEquals("Acme Ltd", business.getName().getValue()))
                .verifyComplete();
    }

    private String BusinessType() {
        return "RESTAURANT";
    }
}
