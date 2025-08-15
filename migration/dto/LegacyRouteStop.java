package biz.ugur.busroutebackend.migration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegacyRouteStop {
    private Long id;
    private Integer routeId;
    private Integer stopId;
    private Integer index;
    private boolean isStartStop; // true = прямое направление, false = обратное
}