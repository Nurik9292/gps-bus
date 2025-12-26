package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import reactor.core.publisher.Mono;

import java.util.List;


public interface GpsDataProvider {


    GpsProviderType getProviderType();

    Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIds(List<String> deviceIds);

    Mono<List<GpsPositionDTO>> fetchAllPositions();


    Mono<Boolean> healthCheck();


    boolean isEnabled();


    default int getPriority() {
        return 100;
    }


    default String getDisplayName() {
        return getProviderType().getDescription();
    }
}
