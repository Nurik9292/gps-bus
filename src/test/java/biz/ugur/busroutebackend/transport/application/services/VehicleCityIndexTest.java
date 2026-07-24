package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.CityId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleCityIndexTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Test
    void resolvesCityOfKnownVehicleAfterRefresh() throws InterruptedException {
        Vehicle arkadag = Vehicle.create("999000000000001", "1561 AKA",
                GpsProviderType.CHINA, CityId.of("city-006"));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(arkadag));
        VehicleCityIndex index = new VehicleCityIndex(vehicleRepository);

        for (int i = 0; i < 50 && index.cityOf(arkadag.getId().getValue()).isEmpty(); i++) {
            Thread.sleep(20);
        }

        assertThat(index.cityOf(arkadag.getId().getValue())).contains("city-006");
    }

    @Test
    void unknownVehicleAndNullIdYieldEmpty() {
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.empty());
        VehicleCityIndex index = new VehicleCityIndex(vehicleRepository);

        assertThat(index.cityOf("missing")).isEmpty();
        assertThat(index.cityOf(null)).isEmpty();
    }

    @Test
    void repositoryFailureLeavesIndexEmptyWithoutThrowing() {
        when(vehicleRepository.findActiveVehicles())
                .thenReturn(Flux.error(new RuntimeException("db down")));
        VehicleCityIndex index = new VehicleCityIndex(vehicleRepository);

        assertThat(index.cityOf("veh-1")).isEmpty();
    }
}
