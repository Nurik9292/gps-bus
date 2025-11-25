package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Wrapper для ответа нового GPS API
 * Новый API возвращает объект с полями code, msg, traceId, data
 */
@Data
public class GpsApiResponseDTO {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("data")
    private List<GpsPositionDTO> data;

    /**
     * Проверка успешности ответа
     */
    public boolean isSuccess() {
        return code != null && code == 1;
    }
}
