package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MobileVehicleInfoResponse {

    private Long  vehicleCount;
    private Long  activeVehicleCount;

}
