package biz.ugur.busroutebackend.business.application.factory;

import biz.ugur.busroutebackend.business.application.dto.CreateBusinessCommand;
import biz.ugur.busroutebackend.business.domain.enums.BusinessType;
import biz.ugur.busroutebackend.business.domain.exceptions.BusinessValidationException;
import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessAddress;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessName;
import biz.ugur.busroutebackend.business.domain.valueobjects.ContactInfo;
import biz.ugur.busroutebackend.business.domain.valueobjects.TaxNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class BusinessFactory {

    private final BusinessRepository businessRepository;

    public BusinessFactory(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    public Mono<Business> create(CreateBusinessCommand cmd) {
        return Mono.defer(() -> {
            BusinessName name = BusinessName.of(cmd.name());
            BusinessType type = BusinessType.from(cmd.businessType());
            ContactInfo contact = ContactInfo.of(
                    cmd.contactPerson(), cmd.contactPhone(), cmd.contactEmail(), cmd.websiteUrl());
            BusinessAddress address = BusinessAddress.of(cmd.country(), cmd.city(), cmd.addressLine());
            TaxNumber tax = cmd.taxNumber() != null && !cmd.taxNumber().isBlank()
                    ? TaxNumber.of(cmd.taxNumber()) : null;

            return ensureUniquePhone(contact.getPhone())
                    .then(tax != null ? ensureUniqueTaxNumber(tax.getValue()) : Mono.empty())
                    .then(Mono.fromCallable(() -> Business.create(
                            name, type, contact, address,
                            tax, cmd.registrationNumber(),
                            cmd.logoUrl(), cmd.description())));
        });
    }

    private Mono<Void> ensureUniquePhone(String phone) {
        return businessRepository.existsByContactPhone(phone)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(BusinessValidationException.singleField(
                                "contactPhone", "already used by another business"))
                        : Mono.empty());
    }

    private Mono<Void> ensureUniqueTaxNumber(String taxNumber) {
        return businessRepository.existsByTaxNumber(taxNumber)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(BusinessValidationException.singleField(
                                "taxNumber", "already used by another business"))
                        : Mono.empty());
    }
}
