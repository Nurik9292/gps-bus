package biz.ugur.busroutebackend.business.application.mapper;

import biz.ugur.busroutebackend.business.application.dto.BusinessResponse;
import biz.ugur.busroutebackend.business.domain.model.Business;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class BusinessResponseMapper {

    public Mono<BusinessResponse> toResponse(Business business) {
        return Mono.fromCallable(() -> BusinessResponse.fromDomain(business));
    }
}
