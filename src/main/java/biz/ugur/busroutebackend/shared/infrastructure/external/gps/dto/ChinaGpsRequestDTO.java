package biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChinaGpsRequestDTO {

    @JsonProperty("id")
    private String id;

    public static ChinaGpsRequestDTO fromDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new ChinaGpsRequestDTO("");
        }
        return new ChinaGpsRequestDTO(String.join(",", deviceIds));
    }
}
