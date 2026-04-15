package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdTariffResponseMapper {

    public Mono<AdTariffResponse> toResponse(AdTariff tariff) {
        return Mono.fromCallable(() -> AdTariffResponse.fromDomain(tariff));
    }
}
